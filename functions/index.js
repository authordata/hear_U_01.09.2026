const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
admin.initializeApp();

const db = admin.firestore();
const MAX_BATCH_SIZE = 400; // Safe under Firestore 500-op limit
const EXPIRED_SESSION_PAGE_SIZE = 50;
const GIVER_PAGE_SIZE = 50;
const MAX_GIVER_PAGES = 10;
const TX_CANDIDATE_CHUNK_SIZE = 25;
const MAX_PURGE_RUNTIME_MS = 50 * 1000; // Exit cleanly before 60s v1 function timeout

/**
 * 1. Matching Algorithm — Authenticated, IDOR-safe, block-aware, transactional double-match guard
 */
exports.matchSeekerWithGiver = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
    }
    if (process.env.ENFORCE_APP_CHECK === "true" && !context.app) {
        throw new functions.https.HttpsError("failed-precondition", "App Check verification required.");
    }
    const seekerId = context.auth.uid;

    // Input validation (safe against null/undefined payload)
    const tags = Array.isArray(data?.tags)
        ? data.tags.filter(t => typeof t === "string").slice(0, 10)
        : [];

    // Load seeker profile (for block list)
    const seekerSnap = await db.collection("users").doc(seekerId).get();
    const seekerData = seekerSnap.exists ? seekerSnap.data() : {};
    const seekerBlocked = new Set(Array.isArray(seekerData?.blockedUsers) ? seekerData.blockedUsers : []);

    // Guard: check for existing active sessions (all of them, to detect stale/blocked ones)
    const existingSessions = await db.collection("sessions")
        .where("seekerId", "==", seekerId)
        .where("status", "==", "active")
        .get();

    let hasStaleExistingSession = false;
    if (!existingSessions.empty) {
        if (existingSessions.docs.length === 1) {
            const existing = existingSessions.docs[0];
            const existingData = existing.data() || {};
            const giverId = existingData.giverId || "";
            if (giverId && !seekerBlocked.has(giverId)) {
                const giverSnap = await db.collection("users").doc(giverId).get();
                const giverData = giverSnap.exists ? (giverSnap.data() || {}) : null;
                const giverBlocked = Array.isArray(giverData?.blockedUsers) ? giverData.blockedUsers : [];
                if (giverSnap.exists && !giverBlocked.includes(seekerId)) {
                    const giverDisplayName = giverData.displayName || "Empathetic Listener";
                    return {
                        matchFound: true,
                        sessionId: existing.id,
                        giverId,
                        giverDisplayName,
                        resumed: true
                    };
                }
            }
            hasStaleExistingSession = true;
        } else {
            hasStaleExistingSession = true;
        }
    }

    const candidates = [];
    let anyOnlineGiver = false;
    let lastGiverDoc = null;

    for (let page = 0; page < MAX_GIVER_PAGES; page++) {
        let giversQuery = db.collection("users")
            .where("role", "in", ["giver", "both"])
            .where("status", "==", "online")
            .limit(GIVER_PAGE_SIZE);
        if (lastGiverDoc) {
            giversQuery = giversQuery.startAfter(lastGiverDoc);
        }

        const giversSnapshot = await giversQuery.get();
        if (giversSnapshot.empty) {
            break;
        }
        anyOnlineGiver = true;

        giversSnapshot.forEach(doc => {
            if (doc.id === seekerId) return;
            const giver = doc.data() || {};
            const giverBlocked = Array.isArray(giver.blockedUsers) ? giver.blockedUsers : [];
            if (seekerBlocked.has(doc.id) || giverBlocked.includes(seekerId)) return;

            const giverTags = Array.isArray(giver.emotionTags) ? giver.emotionTags : [];
            const overlap = tags.filter(t => giverTags.includes(t)).length;
            const rating = typeof giver.rating === "number" && Number.isFinite(giver.rating) ? giver.rating : 5.0;
            const score = (overlap * 35) + (rating * 10) + 20;
            candidates.push({
                id: doc.id,
                displayName: giver.displayName || "Empathetic Listener",
                score
            });
        });

        if (giversSnapshot.docs.length < GIVER_PAGE_SIZE) {
            break;
        }
        lastGiverDoc = giversSnapshot.docs[giversSnapshot.docs.length - 1];
    }

    let noMatchMessage = "No available listener found.";
    if (!anyOnlineGiver) {
        noMatchMessage = "No listeners are currently online.";
    } else if (candidates.length === 0) {
        noMatchMessage = "No suitable listener found.";
    } else {
        candidates.sort((a, b) => b.score - a.score);
    }

    if (candidates.length === 0 && !hasStaleExistingSession) {
        return { matchFound: false, message: noMatchMessage };
    }

    const matchResult = await db.runTransaction(async (tx) => {
        // Re-read seeker inside transaction for consistent block list
        const txSeekerSnap = await tx.get(db.collection("users").doc(seekerId));
        const txSeekerData = txSeekerSnap.exists ? txSeekerSnap.data() : {};
        const txSeekerBlocked = new Set(Array.isArray(txSeekerData?.blockedUsers) ? txSeekerData.blockedUsers : []);

        // Re-verify ALL active sessions for seeker inside transaction
        const seekerActiveSnap = await tx.get(
            db.collection("sessions")
                .where("seekerId", "==", seekerId)
                .where("status", "==", "active")
        );

        const staleSessionRefsToEnd = [];
        let validActiveSession = null;

        for (const existing of seekerActiveSnap.docs) {
            const existingData = existing.data() || {};
            const giverId = existingData.giverId || "";
            if (!giverId || txSeekerBlocked.has(giverId)) {
                staleSessionRefsToEnd.push(existing.ref);
                continue;
            }
            const giverSnap = await tx.get(db.collection("users").doc(giverId));
            const giverData = giverSnap.exists ? (giverSnap.data() || {}) : null;
            const giverBlocked = Array.isArray(giverData?.blockedUsers) ? giverData.blockedUsers : [];
            if (!giverSnap.exists || giverBlocked.includes(seekerId)) {
                staleSessionRefsToEnd.push(existing.ref);
                continue;
            }
            if (!validActiveSession) {
                validActiveSession = {
                    sessionId: existing.id,
                    giverId,
                    giverDisplayName: giverData.displayName || "Empathetic Listener"
                };
            } else {
                staleSessionRefsToEnd.push(existing.ref);
            }
        }

        if (validActiveSession) {
            for (const staleRef of staleSessionRefsToEnd) {
                tx.update(staleRef, { status: "ended" });
            }
            return {
                matchFound: true,
                sessionId: validActiveSession.sessionId,
                giverId: validActiveSession.giverId,
                giverDisplayName: validActiveSession.giverDisplayName,
                resumed: true
            };
        }

        let matchedCandidate = null;
        for (let offset = 0; offset < candidates.length && !matchedCandidate; offset += TX_CANDIDATE_CHUNK_SIZE) {
            const chunk = candidates.slice(offset, offset + TX_CANDIDATE_CHUNK_SIZE);
            const chunkRefs = chunk.map(c => db.collection("users").doc(c.id));
            const chunkSnaps = typeof tx.getAll === "function"
                ? await tx.getAll(...chunkRefs)
                : await Promise.all(chunkRefs.map(ref => tx.get(ref)));

            for (let i = 0; i < chunk.length; i++) {
                const candidate = chunk[i];
                const candidateUserSnap = chunkSnaps[i];
                if (!candidateUserSnap || !candidateUserSnap.exists) continue;
                const candidateData = candidateUserSnap.data() || {};
                if (candidateData.status !== "online") continue;
                const candidateBlocked = Array.isArray(candidateData.blockedUsers) ? candidateData.blockedUsers : [];
                if (txSeekerBlocked.has(candidate.id) || candidateBlocked.includes(seekerId)) continue;

                // Check active sessions as giver or seeker concurrently inside transaction
                const [giverActiveSnap, candidateActiveSeekerSnap] = await Promise.all([
                    tx.get(
                        db.collection("sessions")
                            .where("giverId", "==", candidate.id)
                            .where("status", "==", "active")
                            .limit(1)
                    ),
                    tx.get(
                        db.collection("sessions")
                            .where("seekerId", "==", candidate.id)
                            .where("status", "==", "active")
                            .limit(1)
                    )
                ]);
                if (!giverActiveSnap.empty || !candidateActiveSeekerSnap.empty) continue;

                matchedCandidate = {
                    id: candidate.id,
                    displayName: candidateData.displayName || candidate.displayName
                };
                break;
            }
        }

        // All tx.get(...) reads are complete; perform transactional writes now
        for (const staleRef of staleSessionRefsToEnd) {
            tx.update(staleRef, { status: "ended" });
        }

        if (matchedCandidate) {
            const sessionRef = db.collection("sessions").doc();
            tx.set(sessionRef, {
                seekerId,
                giverId: matchedCandidate.id,
                status: "active",
                createdAt: admin.firestore.Timestamp.now(),
                identityRevealed: false,
                seekerConsent: false,
                giverConsent: false
            });

            return {
                matchFound: true,
                sessionId: sessionRef.id,
                giverId: matchedCandidate.id,
                giverDisplayName: matchedCandidate.displayName,
                resumed: false
            };
        }

        return { matchFound: false, message: noMatchMessage };
    });

    return matchResult;
});

/**
 * 2. 30-Day Auto-Delete — Paginated loop with chunked batches under 500-op Firestore limit
 */
exports.purgeExpiredChats = functions.pubsub.schedule("every 24 hours").onRun(async () => {
    const startMs = Date.now();
    const thirtyDaysAgo = admin.firestore.Timestamp.fromMillis(
        startMs - (30 * 24 * 60 * 60 * 1000)
    );

    let totalSessionsPurged = 0;
    let totalDocsDeleted = 0;

    while (Date.now() - startMs < MAX_PURGE_RUNTIME_MS) {
        const expiredSessions = await db.collection("sessions")
            .where("createdAt", "<=", thirtyDaysAgo)
            .limit(EXPIRED_SESSION_PAGE_SIZE)
            .get();

        if (expiredSessions.empty) {
            break;
        }

        for (const sessionDoc of expiredSessions.docs) {
            if (Date.now() - startMs >= MAX_PURGE_RUNTIME_MS) {
                break;
            }
            // Paginate message subcollection deletion to avoid unbounded memory usage
            while (true) {
                const msgSnap = await sessionDoc.ref
                    .collection("messages")
                    .limit(MAX_BATCH_SIZE)
                    .get();

                if (msgSnap.empty) {
                    break;
                }

                const batch = db.batch();
                msgSnap.docs.forEach(msg => batch.delete(msg.ref));
                await batch.commit();
                totalDocsDeleted += msgSnap.size;
            }

            await sessionDoc.ref.delete();
            totalSessionsPurged += 1;
            totalDocsDeleted += 1;
        }
    }

    if (totalSessionsPurged === 0) {
        console.log("No expired sessions to purge.");
    } else {
        console.log(`Purged ${totalSessionsPurged} sessions (${totalDocsDeleted} total documents).`);
    }
    return null;
});

/**
 * 3. Moderation Alert — Log sanitized metadata only (no PII)
 */
exports.onReportSubmitted = functions.firestore.document("reports/{reportId}").onCreate(async (snap, context) => {
    // Log only report ID and timestamp — never log raw reason or user content
    console.warn(`[SAFETY ALERT] Report submitted: documentId=${context.params.reportId}, timestamp=${new Date().toISOString()}`);
    return null;
});
