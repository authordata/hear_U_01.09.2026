const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

const db = admin.firestore();

/**
 * 1. Matching Algorithm Cloud Function
 * Evaluates candidate Givers securely using authenticated context
 */
exports.matchSeekerWithGiver = functions.https.onCall(async (data, context) => {
    // Enforce authentication & prevent IDOR
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be authenticated to find a match.");
    }
    const seekerId = context.auth.uid;
    const { tags } = data;

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
        const giver = doc.data();
        if (doc.id === seekerId) return; // Cannot match with self

        let score = 0;
        const giverTags = giver.emotionTags || [];
        const overlap = tags ? tags.filter(t => giverTags.includes(t)).length : 0;
        score += overlap * 35; // 35% weight for topic overlap
        score += (giver.rating || 5.0) * 10; // 10% weight for ratings
        score += 20; // 20% base for being online

        if (score > highestScore) {
            highestScore = score;
            bestGiver = { id: doc.id, ...giver };
        }
    });

    if (!bestGiver) return { matchFound: false };

    // Create active session
    const sessionRef = await db.collection("sessions").add({
        seekerId: seekerId,
        giverId: bestGiver.id,
        status: "active",
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        identityRevealed: false
    });

    return { matchFound: true, sessionId: sessionRef.id, giverDisplayName: bestGiver.displayName };
});

/**
 * 2. 30-Day Auto-Delete Daily Cron Job (Optimized concurrent batching)
 */
exports.purgeExpiredChats = functions.pubsub.schedule("every 24 hours").onRun(async (context) => {
    const thirtyDaysAgo = new Date(Date.now() - (30 * 24 * 60 * 60 * 1000));
    
    const expiredSessions = await db.collection("sessions")
        .where("createdAt", "<=", thirtyDaysAgo)
        .limit(100)
        .get();

    if (expiredSessions.empty) return null;

    const batch = db.batch();
    
    // Fetch all subcollection messages in parallel
    const subcollectionPromises = expiredSessions.docs.map(async (sessionDoc) => {
        const messages = await sessionDoc.ref.collection("messages").get();
        messages.forEach(msg => batch.delete(msg.ref));
        batch.delete(sessionDoc.ref);
    });

    await Promise.all(subcollectionPromises);
    await batch.commit();
    console.log(`Successfully purged ${expiredSessions.size} expired chat sessions.`);
    return null;
});

/**
 * 3. Moderation Alert Webhook
 */
exports.onReportSubmitted = functions.firestore.document("reports/{reportId}").onCreate(async (snap, context) => {
    const report = snap.data();
    console.warn(`[SAFETY ALERT] User ${report.reportedUserId} reported by ${report.reporterId} for: ${report.reason}`);
    return null;
});
