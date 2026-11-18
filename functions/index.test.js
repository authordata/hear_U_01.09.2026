const test = require("node:test");
const assert = require("node:assert/strict");
const Module = require("node:module");
const path = require("node:path");

function cloneData(val) {
    if (val === null || val === undefined || typeof val !== "object") return val;
    if (typeof val.toMillis === "function") return { toMillis: () => val.toMillis() };
    if (Array.isArray(val)) return val.map(cloneData);
    const out = {};
    for (const [k, v] of Object.entries(val)) {
        out[k] = cloneData(v);
    }
    return out;
}

function createInMemoryFirestore() {
    const collections = new Map();
    let autoIdCounter = 1;

    function getColMap(name) {
        if (!collections.has(name)) collections.set(name, new Map());
        return collections.get(name);
    }

    class DocSnapshot {
        constructor(id, data, ref) {
            this.id = id;
            this._data = data ? cloneData(data) : undefined;
            this.ref = ref;
            this.exists = data !== undefined;
        }
        data() {
            return this._data ? cloneData(this._data) : undefined;
        }
    }

    class QuerySnapshot {
        constructor(docs) {
            this.docs = docs;
            this.empty = docs.length === 0;
            this.size = docs.length;
        }
        forEach(cb) {
            this.docs.forEach(cb);
        }
    }

    class DocRef {
        constructor(colPath, id) {
            this.colPath = colPath;
            this.id = id;
        }
        collection(sub) {
            return new CollectionRef(`${this.colPath}/${this.id}/${sub}`);
        }
        async get() {
            const data = getColMap(this.colPath).get(this.id);
            return new DocSnapshot(this.id, data, this);
        }
        async set(data) {
            getColMap(this.colPath).set(this.id, cloneData(data));
        }
        async update(fields) {
            const map = getColMap(this.colPath);
            const cur = map.get(this.id) || {};
            map.set(this.id, { ...cur, ...cloneData(fields) });
        }
        async delete() {
            getColMap(this.colPath).delete(this.id);
        }
    }

    class QueryRef {
        constructor(colPath, filters = [], limitCount = null, startAfterId = null) {
            this.colPath = colPath;
            this.filters = filters;
            this.limitCount = limitCount;
            this.startAfterId = startAfterId;
        }
        where(field, op, value) {
            return new QueryRef(this.colPath, [...this.filters, { field, op, value }], this.limitCount, this.startAfterId);
        }
        limit(n) {
            return new QueryRef(this.colPath, this.filters, n, this.startAfterId);
        }
        startAfter(docSnap) {
            return new QueryRef(this.colPath, this.filters, this.limitCount, docSnap ? docSnap.id : null);
        }
        async get() {
            const entries = Array.from(getColMap(this.colPath).entries());
            let matched = entries.filter(([, data]) => {
                return this.filters.every(({ field, op, value }) => {
                    const actual = data[field];
                    if (op === "==") return actual === value;
                    if (op === "in") return Array.isArray(value) && value.includes(actual);
                    if (op === "<=") {
                        const a = actual && typeof actual.toMillis === "function" ? actual.toMillis() : actual;
                        const b = value && typeof value.toMillis === "function" ? value.toMillis() : value;
                        return a <= b;
                    }
                    return false;
                });
            });

            if (this.startAfterId !== null) {
                const idx = matched.findIndex(([id]) => id === this.startAfterId);
                matched = idx >= 0 ? matched.slice(idx + 1) : [];
            }
            if (typeof this.limitCount === "number") {
                matched = matched.slice(0, this.limitCount);
            }
            const snaps = matched.map(([id, data]) => new DocSnapshot(id, data, new DocRef(this.colPath, id)));
            return new QuerySnapshot(snaps);
        }
    }

    class CollectionRef extends QueryRef {
        constructor(colPath) {
            super(colPath);
        }
        doc(id) {
            const resolvedId = id || `auto_${autoIdCounter++}`;
            return new DocRef(this.colPath, resolvedId);
        }
    }

    const db = {
        collection(name) {
            return new CollectionRef(name);
        },
        batch() {
            const ops = [];
            return {
                delete(ref) {
                    ops.push(() => ref.delete());
                },
                async commit() {
                    for (const op of ops) await op();
                }
            };
        },
        async runTransaction(fn) {
            const pending = [];
            const tx = {
                async get(refOrQuery) {
                    return refOrQuery.get();
                },
                async getAll(...refs) {
                    return Promise.all(refs.map(r => r.get()));
                },
                set(ref, data) {
                    pending.push(() => ref.set(data));
                },
                update(ref, fields) {
                    pending.push(() => ref.update(fields));
                }
            };
            const res = await fn(tx);
            for (const op of pending) await op();
            return res;
        }
    };

    return { db, collections };
}

function loadFunctionsWithMock() {
    const { db, collections } = createInMemoryFirestore();

    class HttpsError extends Error {
        constructor(code, message) {
            super(message);
            this.code = code;
        }
    }

    const functionsMock = {
        https: {
            HttpsError,
            onCall: (handler) => handler
        },
        pubsub: {
            schedule: () => ({
                onRun: (handler) => handler
            })
        },
        firestore: {
            document: () => ({
                onCreate: (handler) => handler
            })
        }
    };

    const adminMock = {
        initializeApp: () => {},
        firestore: Object.assign(() => db, {
            Timestamp: {
                now: () => {
                    const ms = Date.now();
                    return { toMillis: () => ms };
                },
                fromMillis: (ms) => ({ toMillis: () => ms })
            }
        })
    };

    const originalLoad = Module._load;
    Module._load = function (request, parent, isMain) {
        if (request === "firebase-functions/v1") return functionsMock;
        if (request === "firebase-admin") return adminMock;
        return originalLoad.apply(this, arguments);
    };

    const indexPath = path.join(__dirname, "index.js");
    delete require.cache[require.resolve(indexPath)];
    try {
        const exported = require(indexPath);
        return { exported, db, collections, HttpsError };
    } finally {
        Module._load = originalLoad;
    }
}

test("matchSeekerWithGiver rejects unauthenticated calls", async () => {
    const { exported, HttpsError } = loadFunctionsWithMock();
    await assert.rejects(
        () => exported.matchSeekerWithGiver({ tags: [] }, { auth: null }),
        (err) => err instanceof HttpsError && err.code === "unauthenticated"
    );
});

test("matchSeekerWithGiver enforces App Check when ENFORCE_APP_CHECK is enabled", async () => {
    const { exported, HttpsError } = loadFunctionsWithMock();
    const prev = process.env.ENFORCE_APP_CHECK;
    process.env.ENFORCE_APP_CHECK = "true";
    try {
        await assert.rejects(
            () => exported.matchSeekerWithGiver({ tags: [] }, { auth: { uid: "u1" }, app: null }),
            (err) => err instanceof HttpsError && err.code === "failed-precondition"
        );
    } finally {
        if (prev === undefined) delete process.env.ENFORCE_APP_CHECK;
        else process.env.ENFORCE_APP_CHECK = prev;
    }
});

test("matchSeekerWithGiver never double-books a 'both' user currently active as seeker", async () => {
    const { exported, db } = loadFunctionsWithMock();

    await db.collection("users").doc("seeker_req").set({
        uid: "seeker_req",
        role: "seeker",
        status: "offline",
        blockedUsers: []
    });
    await db.collection("users").doc("u_both").set({
        uid: "u_both",
        displayName: "DualUser",
        role: "both",
        status: "online",
        emotionTags: ["Anxiety"],
        blockedUsers: []
    });
    await db.collection("sessions").doc("sess_active_seeker").set({
        seekerId: "u_both",
        giverId: "other_giver",
        status: "active"
    });

    const result = await exported.matchSeekerWithGiver(
        { tags: ["Anxiety"] },
        { auth: { uid: "seeker_req" } }
    );
    assert.equal(result.matchFound, false);
});

test("matchSeekerWithGiver paginates past >50 busy givers to match giver_51", async () => {
    const { exported, db } = loadFunctionsWithMock();

    await db.collection("users").doc("seeker_1").set({
        uid: "seeker_1",
        role: "seeker",
        status: "offline",
        blockedUsers: []
    });

    for (let i = 1; i <= 50; i++) {
        const gid = `giver_${String(i).padStart(2, "0")}`;
        await db.collection("users").doc(gid).set({
            uid: gid,
            displayName: `Busy ${i}`,
            role: "giver",
            status: "online",
            rating: 5.0,
            blockedUsers: []
        });
        await db.collection("sessions").doc(`busy_sess_${i}`).set({
            seekerId: `other_seeker_${i}`,
            giverId: gid,
            status: "active"
        });
    }

    await db.collection("users").doc("giver_51").set({
        uid: "giver_51",
        displayName: "Available Giver 51",
        role: "giver",
        status: "online",
        rating: 4.9,
        blockedUsers: []
    });

    const result = await exported.matchSeekerWithGiver(
        { tags: [] },
        { auth: { uid: "seeker_1" } }
    );
    assert.equal(result.matchFound, true);
    assert.equal(result.giverId, "giver_51");
    assert.equal(result.giverDisplayName, "Available Giver 51");
    assert.equal(result.resumed, false);
});

test("purgeExpiredChats paginates across >100 expired sessions and message subcollections", async () => {
    const { exported, db, collections } = loadFunctionsWithMock();
    const expiredTs = Date.now() - (40 * 24 * 60 * 60 * 1000);

    for (let i = 1; i <= 110; i++) {
        const sid = `exp_${i}`;
        await db.collection("sessions").doc(sid).set({
            seekerId: "s",
            giverId: "g",
            status: "ended",
            createdAt: expiredTs
        });
        await db.collection("sessions").doc(sid).collection("messages").doc("m1").set({
            senderId: "s",
            text: "old"
        });
    }
    await db.collection("sessions").doc("fresh_1").set({
        seekerId: "s",
        giverId: "g",
        status: "active",
        createdAt: Date.now()
    });

    await exported.purgeExpiredChats();

    const remainingSessions = collections.get("sessions");
    assert.equal(remainingSessions.size, 1);
    assert.ok(remainingSessions.has("fresh_1"));
});

test("onReportSubmitted logs only sanitized metadata without PII", async () => {
    const { exported } = loadFunctionsWithMock();
    const warnings = [];
    const origWarn = console.warn;
    console.warn = (msg) => warnings.push(String(msg));
    try {
        await exported.onReportSubmitted(
            {
                data: () => ({
                    reporterId: "secret_reporter_uid",
                    reportedUserId: "secret_reported_uid",
                    reason: "Sensitive PII reason"
                })
            },
            { params: { reportId: "rep_42" } }
        );
    } finally {
        console.warn = origWarn;
    }

    assert.equal(warnings.length, 1);
    assert.ok(warnings[0].includes("rep_42"));
    assert.ok(!warnings[0].includes("secret_reporter_uid"));
    assert.ok(!warnings[0].includes("secret_reported_uid"));
    assert.ok(!warnings[0].includes("Sensitive PII reason"));
});
