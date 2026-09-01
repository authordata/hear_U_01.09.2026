const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

const db = admin.firestore();
const MAX_BATCH_SIZE = 400; // Safe under Firestore 500-op limit

/**
 * 1. Matching Algorithm — Authenticated, IDOR-safe, concurrent session guard
 */
exports.matchSeekerWithGiver = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
    }
    const seekerId = context.auth.uid;

    // Input validation
    const tags = Array.isArray(data.tags) ? data.tags.filter(t => typeof t === 'string').slice(0, 10) : [];

    // Guard: check for existing active session
    const existingSession = await db.collection("sessions")
        .where("seekerId", "==", seekerId)
        .where("status", "==", "active")
        .limit(1)
        .get();

    if (!existingSession.empty) {
        const existing = existingSession.docs[0];
        return { matchFound: true, sessionId: existing.id, resumed: true };
    }

    const giversSnapshot = await db.collection("users")
        .where("role", "in", ["giver", "both"])
        .where("status", "==", "online")
        .limit(20)
        .get();

    if (giversSnapshot.empty) {
        return { matchFound: false, message: "No listeners are currently online." };
    }

    let bestGiver = null;
    let highestScore = -1;

    giversSnapshot.forEach(doc => {
        if (doc.id === seekerId) return;
        const giver = doc.data();
        const giverTags = giver.emotionTags || [];
        const overlap = tags.filter(t => giverTags.includes(t)).length;
        const score = (overlap * 35) + ((giver.rating || 5.0) * 10) + 20;
        if (score > highestScore) {
            highestScore = score;
            bestGiver = { id: doc.id, displayName: giver.displayName };
        }
    });

    if (!bestGiver) return { matchFound: false, message: "No suitable listener found." };

    const sessionRef = await db.collection("sessions").add({
        seekerId,
        giverId: bestGiver.id,
        status: "active",
        createdAt: admin.firestore.Timestamp.now(),
        identityRevealed: false
    });

    return { matchFound: true, sessionId: sessionRef.id, giverDisplayName: bestGiver.displayName };
});

/**
 * 2. 30-Day Auto-Delete — Chunked batch to stay under 500-op Firestore limit
 */
exports.purgeExpiredChats = functions.pubsub.schedule("every 24 hours").onRun(async () => {
    const thirtyDaysAgo = admin.firestore.Timestamp.fromMillis(
        Date.now() - (30 * 24 * 60 * 60 * 1000)
    );

    const expiredSessions = await db.collection("sessions")
        .where("createdAt", "<=", thirtyDaysAgo)
        .limit(100)
        .get();

    if (expiredSessions.empty) {
        console.log("No expired sessions to purge.");
        return null;
    }

    // Collect all delete refs
    const allRefs = [];
    for (const sessionDoc of expiredSessions.docs) {
        const messages = await sessionDoc.ref.collection("messages").get();
        messages.forEach(msg => allRefs.push(msg.ref));
        allRefs.push(sessionDoc.ref);
    }

    // Chunk into batches of MAX_BATCH_SIZE
    for (let i = 0; i < allRefs.length; i += MAX_BATCH_SIZE) {
        const chunk = allRefs.slice(i, i + MAX_BATCH_SIZE);
        const batch = db.batch();
        chunk.forEach(ref => batch.delete(ref));
        await batch.commit();
    }

    console.log(`Purged ${expiredSessions.size} sessions (${allRefs.length} total documents).`);
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
