import React, { useEffect, useMemo, useState } from "react";
import {
  onAuthStateChanged,
  signInWithPopup,
  signOut,
  type User,
} from "firebase/auth";
import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  getDoc,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
} from "firebase/firestore";
import { httpsCallable } from "firebase/functions";
import {
  Activity,
  Bell,
  CheckCircle2,
  FileText,
  LogOut,
  Megaphone,
  MessageSquare,
  ShieldAlert,
  Sliders,
  Trash2,
  Users,
  Wrench,
} from "lucide-react";
import { auth, db, functions, googleProvider } from "./firebase";
import {
  DEFAULT_GLOBAL_CONFIG,
  type Announcement,
  type AppGlobalConfig,
  type UserFeedback,
  type UserProfile,
} from "./types";

type AdminTab =
  | "overview"
  | "config"
  | "announcements"
  | "notifications"
  | "feedback"
  | "users";

function formatBytes(bytes: number): string {
  if (!bytes) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(k)), 3);
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
}

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [isAdmin, setIsAdmin] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);

  const [tab, setTab] = useState<AdminTab>("overview");
  const [users, setUsers] = useState<UserProfile[]>([]);
  const [config, setConfig] = useState<AppGlobalConfig>(DEFAULT_GLOBAL_CONFIG);
  const [announcements, setAnnouncements] = useState<Announcement[]>([]);
  const [feedback, setFeedback] = useState<UserFeedback[]>([]);
  const [toast, setToast] = useState<string | null>(null);

  // Forms
  const [annTitle, setAnnTitle] = useState("");
  const [annMessage, setAnnMessage] = useState("");
  const [annSeverity, setAnnSeverity] = useState<"info" | "warning" | "update">("info");

  const [notifTitle, setNotifTitle] = useState("");
  const [notifBody, setNotifBody] = useState("");
  const [notifTopic, setNotifTopic] = useState("all_users");

  const [userSearch, setUserSearch] = useState("");

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, async (current) => {
      setUser(current);
      setAuthError(null);
      if (!current) {
        setIsAdmin(false);
        setAuthLoading(false);
        return;
      }
      try {
        const adminSnap = await getDoc(doc(db, "admins", current.uid));
        const tokenResult = await current.getIdTokenResult();
        const isBootstrappedAdmin =
          current.email === "soumyadipacharya481@gmail.com" && current.emailVerified;
        const verified =
          adminSnap.exists() ||
          tokenResult.claims.admin === true ||
          isBootstrappedAdmin;
        setIsAdmin(verified);
        if (!verified) {
          setAuthError("Access denied: your account does not have Admin role privileges.");
        }
      } catch (err: unknown) {
        setIsAdmin(false);
        setAuthError(err instanceof Error ? err.message : "Failed to verify admin role.");
      } finally {
        setAuthLoading(false);
      }
    });
    return () => unsub();
  }, []);

  useEffect(() => {
    if (!user || !isAdmin) return;

    const unsubUsers = onSnapshot(collection(db, "users"), (snap) => {
      setUsers(
        snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<UserProfile, "id">) }))
      );
    });

    const unsubConfig = onSnapshot(doc(db, "app_config", "global"), (snap) => {
      if (snap.exists()) {
        setConfig({ ...DEFAULT_GLOBAL_CONFIG, ...(snap.data() as AppGlobalConfig) });
      }
    });

    const unsubAnn = onSnapshot(
      query(collection(db, "announcements"), orderBy("createdAt", "desc")),
      (snap) => {
        setAnnouncements(
          snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Announcement, "id">) }))
        );
      }
    );

    const unsubFb = onSnapshot(
      query(collection(db, "feedback"), orderBy("createdAt", "desc")),
      (snap) => {
        setFeedback(
          snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<UserFeedback, "id">) }))
        );
      }
    );

    return () => {
      unsubUsers();
      unsubConfig();
      unsubAnn();
      unsubFb();
    };
  }, [user, isAdmin]);

  const metrics = useMemo(() => {
    const now = Date.now();
    const sevenDaysMs = 7 * 24 * 60 * 60 * 1000;
    const activeUsers = users.filter((u) => {
      const ms = u.lastActiveAt?.toMillis?.() ?? 0;
      return now - ms <= sevenDaysMs;
    }).length;
    const totalPdfs = users.reduce((acc, u) => acc + (u.pdfsCreatedCount || 0), 0);
    const totalBytes = users.reduce((acc, u) => acc + (u.totalBytesProcessed || 0), 0);
    const openFeedback = feedback.filter((f) => f.status !== "resolved").length;
    return {
      totalUsers: users.length,
      activeUsers,
      totalPdfs,
      totalBytes,
      openFeedback,
    };
  }, [users, feedback]);

  const showToast = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(null), 3500);
  };

  const handleSaveConfig = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await setDoc(
        doc(db, "app_config", "global"),
        { ...config, updatedAt: serverTimestamp() },
        { merge: true }
      );
      showToast("Global configuration saved.");
    } catch (err: unknown) {
      showToast(err instanceof Error ? err.message : "Failed to save config.");
    }
  };

  const handleCreateAnnouncement = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !annTitle.trim() || !annMessage.trim()) return;
    try {
      await addDoc(collection(db, "announcements"), {
        title: annTitle.trim(),
        message: annMessage.trim(),
        severity: annSeverity,
        isActive: true,
        createdBy: user.uid,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      setAnnTitle("");
      setAnnMessage("");
      showToast("Announcement published.");
    } catch (err: unknown) {
      showToast(err instanceof Error ? err.message : "Failed to create announcement.");
    }
  };

  const handleSendPush = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!notifTitle.trim() || !notifBody.trim()) return;
    try {
      const callable = httpsCallable(functions, "sendPushNotification");
      await callable({
        topic: notifTopic,
        title: notifTitle.trim(),
        body: notifBody.trim(),
      });
      setNotifTitle("");
      setNotifBody("");
      showToast("Push notification dispatched via Firebase Cloud Messaging.");
    } catch (err: unknown) {
      showToast(err instanceof Error ? err.message : "Notification dispatch failed.");
    }
  };

  const handleToggleUserStatus = async (target: UserProfile) => {
    const nextStatus = target.status === "suspended" ? "active" : "suspended";
    try {
      const callable = httpsCallable(functions, "setUserAccountStatus");
      await callable({ targetUid: target.userId, status: nextStatus });
      showToast(`User ${target.displayName} is now ${nextStatus}.`);
    } catch {
      await updateDoc(doc(db, "users", target.id), {
        status: nextStatus,
        updatedAt: serverTimestamp(),
      });
      showToast(`Updated ${target.displayName} status to ${nextStatus}.`);
    }
  };

  const handleResolveFeedback = async (item: UserFeedback, reply: string) => {
    await updateDoc(doc(db, "feedback", item.id), {
      status: "resolved",
      adminReply: reply || "Resolved by Admin",
      updatedAt: serverTimestamp(),
    });
    showToast("Feedback marked resolved.");
  };

  if (authLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center text-slate-300">
        Verifying admin session...
      </div>
    );
  }

  if (!user || !isAdmin) {
    return (
      <div className="flex min-h-screen items-center justify-center p-6">
        <div className="glass-card w-full max-w-md rounded-3xl p-8 shadow-2xl">
          <div className="mb-6 flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-violet-600/20 text-violet-400">
              <ShieldAlert className="h-6 w-6" />
            </div>
            <div>
              <h1 className="text-xl font-bold text-white">PDF Maker Admin</h1>
              <p className="text-sm text-slate-400">Role-Based Secure Console</p>
            </div>
          </div>

          {authError && (
            <div className="mb-4 rounded-xl border border-rose-500/30 bg-rose-500/10 p-3 text-sm text-rose-300">
              {authError}
            </div>
          )}

          <p className="mb-6 text-sm text-slate-300">
            Sign in with your authorized Google Administrator account. Access is enforced by
            Firestore security rules and Firebase Admin SDK role checks.
          </p>

          <button
            onClick={() => signInWithPopup(auth, googleProvider)}
            className="flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-slate-900 via-violet-700 to-indigo-600 px-5 py-3.5 text-sm font-semibold text-white shadow-lg transition hover:opacity-95"
          >
            Sign in with Google Admin Account
          </button>

          {user && (
            <button
              onClick={() => signOut(auth)}
              className="mt-3 w-full rounded-xl border border-white/10 py-2.5 text-xs text-slate-400 hover:text-white"
            >
              Sign out ({user.email})
            </button>
          )}
        </div>
      </div>
    );
  }

  const filteredUsers = users.filter(
    (u) =>
      u.displayName?.toLowerCase().includes(userSearch.toLowerCase()) ||
      u.email?.toLowerCase().includes(userSearch.toLowerCase())
  );

  return (
    <div className="flex min-h-screen flex-col md:flex-row">
      {/* Sidebar */}
      <aside className="glass-card m-3 flex flex-col justify-between rounded-3xl p-5 md:w-64">
        <div>
          <div className="mb-6 flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-600 text-white font-bold">
              PDF
            </div>
            <div>
              <h1 className="font-bold text-white">PDF Maker</h1>
              <p className="text-xs text-violet-300">Admin Console</p>
            </div>
          </div>

          <nav className="space-y-1.5">
            {(
              [
                ["overview", "Overview", Activity],
                ["config", "App & Maintenance", Sliders],
                ["announcements", "Announcements", Megaphone],
                ["notifications", "Push Notifications", Bell],
                ["feedback", "Feedback", MessageSquare],
                ["users", "User Management", Users],
              ] as const
            ).map(([key, label, IconComp]) => (
              <button
                key={key}
                onClick={() => setTab(key)}
                className={`flex w-full items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-medium transition ${
                  tab === key
                    ? "bg-violet-600 text-white shadow-md"
                    : "text-slate-300 hover:bg-white/5"
                }`}
              >
                <IconComp className="h-4 w-4" />
                {label}
              </button>
            ))}
          </nav>
        </div>

        <div className="mt-6 border-t border-white/10 pt-4">
          <p className="truncate text-xs text-slate-400">{user.email}</p>
          <button
            onClick={() => signOut(auth)}
            className="mt-2 flex w-full items-center gap-2 rounded-xl border border-white/10 px-3 py-2 text-xs text-slate-300 hover:bg-white/5"
          >
            <LogOut className="h-3.5 w-3.5" />
            Sign Out
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 p-4 md:p-6">
        {toast && (
          <div className="mb-4 flex items-center gap-2 rounded-2xl border border-emerald-500/30 bg-emerald-500/15 px-4 py-3 text-sm text-emerald-200">
            <CheckCircle2 className="h-4 w-4" />
            {toast}
          </div>
        )}

        {tab === "overview" && (
          <div className="space-y-6">
            <h2 className="text-2xl font-bold text-white">Dashboard Overview</h2>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <StatCard title="Registered Users" value={String(metrics.totalUsers)} icon={<Users className="h-5 w-5 text-violet-400" />} />
              <StatCard title="Active Users (7d)" value={String(metrics.activeUsers)} icon={<Activity className="h-5 w-5 text-emerald-400" />} />
              <StatCard title="PDFs Processed" value={String(metrics.totalPdfs)} icon={<FileText className="h-5 w-5 text-sky-400" />} />
              <StatCard title="Open Feedback" value={String(metrics.openFeedback)} icon={<MessageSquare className="h-5 w-5 text-amber-400" />} />
            </div>

            <div className="glass-card rounded-3xl p-6">
              <h3 className="mb-2 text-lg font-semibold text-white">Platform Usage Summary</h3>
              <p className="text-sm text-slate-300">
                Total volume processed locally by synced devices:{" "}
                <span className="font-mono font-semibold text-violet-300">
                  {formatBytes(metrics.totalBytes)}
                </span>
              </p>
              <div className="mt-4 grid gap-3 sm:grid-cols-3">
                <div className="rounded-2xl bg-slate-900/60 p-4">
                  <p className="text-xs text-slate-400">Maintenance Mode</p>
                  <p className="mt-1 font-semibold text-white">
                    {config.maintenanceMode ? "ENABLED" : "Operational"}
                  </p>
                </div>
                <div className="rounded-2xl bg-slate-900/60 p-4">
                  <p className="text-xs text-slate-400">Latest App Version</p>
                  <p className="mt-1 font-mono font-semibold text-white">
                    v{config.latestVersionName} (Build {config.latestVersionCode})
                  </p>
                </div>
                <div className="rounded-2xl bg-slate-900/60 p-4">
                  <p className="text-xs text-slate-400">Active Announcements</p>
                  <p className="mt-1 font-semibold text-white">
                    {announcements.filter((a) => a.isActive).length}
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}

        {tab === "config" && (
          <form onSubmit={handleSaveConfig} className="glass-card max-w-3xl space-y-5 rounded-3xl p-6">
            <div className="flex items-center gap-2">
              <Wrench className="h-5 w-5 text-violet-400" />
              <h2 className="text-xl font-bold text-white">Version, Feature Flags & Maintenance</h2>
            </div>

            <label className="flex items-center justify-between rounded-2xl bg-slate-900/60 p-4">
              <div>
                <p className="font-medium text-white">Maintenance Mode</p>
                <p className="text-xs text-slate-400">Displays maintenance notice to synced Android clients</p>
              </div>
              <input
                type="checkbox"
                checked={config.maintenanceMode}
                onChange={(e) => setConfig({ ...config, maintenanceMode: e.target.checked })}
                className="h-5 w-5 accent-violet-600"
              />
            </label>

            <div>
              <label className="mb-1 block text-xs text-slate-400">Maintenance Message</label>
              <input
                type="text"
                value={config.maintenanceMessage}
                onChange={(e) => setConfig({ ...config, maintenanceMessage: e.target.value })}
                className="w-full rounded-xl border border-white/10 bg-slate-900/70 px-3.5 py-2.5 text-sm text-white"
              />
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              <div>
                <label className="mb-1 block text-xs text-slate-400">Latest Version Name</label>
                <input
                  type="text"
                  value={config.latestVersionName}
                  onChange={(e) => setConfig({ ...config, latestVersionName: e.target.value })}
                  className="w-full rounded-xl border border-white/10 bg-slate-900/70 px-3 py-2 text-sm text-white"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-slate-400">Latest Version Code</label>
                <input
                  type="number"
                  value={config.latestVersionCode}
                  onChange={(e) => setConfig({ ...config, latestVersionCode: Number(e.target.value) })}
                  className="w-full rounded-xl border border-white/10 bg-slate-900/70 px-3 py-2 text-sm text-white"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-slate-400">Min Supported Code</label>
                <input
                  type="number"
                  value={config.minSupportedVersionCode}
                  onChange={(e) => setConfig({ ...config, minSupportedVersionCode: Number(e.target.value) })}
                  className="w-full rounded-xl border border-white/10 bg-slate-900/70 px-3 py-2 text-sm text-white"
                />
              </div>
            </div>

            <label className="flex items-center gap-3 text-sm text-slate-200">
              <input
                type="checkbox"
                checked={config.forceUpdate}
                onChange={(e) => setConfig({ ...config, forceUpdate: e.target.checked })}
                className="h-4 w-4 accent-violet-600"
              />
              Enforce Update Prompt when client build is below Min Supported Code
            </label>

            <div>
              <label className="mb-2 block text-xs uppercase tracking-wider text-slate-400">
                Feature Configuration
              </label>
              <div className="grid gap-2 sm:grid-cols-2">
                {(
                  [
                    ["enableImageToPdf", "Image to PDF Maker"],
                    ["enableCompress", "PDF Compressor"],
                    ["enableMerge", "Merge PDF"],
                    ["enableSplit", "Split PDF"],
                    ["enableOrganizer", "Page Organizer"],
                    ["enablePasswordProtect", "Password Protection"],
                  ] as const
                ).map(([key, label]) => (
                  <label key={key} className="flex items-center justify-between rounded-xl bg-slate-900/50 px-3.5 py-2.5 text-sm">
                    <span>{label}</span>
                    <input
                      type="checkbox"
                      checked={config[key]}
                      onChange={(e) => setConfig({ ...config, [key]: e.target.checked })}
                      className="h-4 w-4 accent-violet-600"
                    />
                  </label>
                ))}
              </div>
            </div>

            <button
              type="submit"
              className="rounded-2xl bg-violet-600 px-6 py-3 text-sm font-semibold text-white hover:bg-violet-500"
            >
              Save Configuration
            </button>
          </form>
        )}

        {tab === "announcements" && (
          <div className="space-y-6">
            <form onSubmit={handleCreateAnnouncement} className="glass-card max-w-2xl space-y-4 rounded-3xl p-6">
              <h2 className="text-xl font-bold text-white">Publish In-App Announcement</h2>
              <input
                type="text"
                placeholder="Announcement Title"
                value={annTitle}
                onChange={(e) => setAnnTitle(e.target.value)}
                className="w-full rounded-xl border border-white/10 bg-slate-900/70 px-3.5 py-2.5 text-sm text-white"
              />
              <textarea
                placeholder="Message shown on Android Home Dashboard"
                value={annMessage}
                onChange={(e) => setAnnMessage(e.target.value)}
                rows={3}
                className="w-full rounded-xl border border-white/10 bg-slate-900/70 px-3.5 py-2.5 text-sm text-white"
              />
              <div className="flex items-center gap-3">
                <select
                  value={annSeverity}
                  onChange={(e) => setAnnSeverity(e.target.value as "info" | "warning" | "update")}
                  className="rounded-xl border border-white/10 bg-slate-900/70 px-3 py-2 text-sm text-white"
                >
                  <option value="info">Info</option>
                  <option value="update">Update</option>
                  <option value="warning">Warning</option>
                </select>
                <button type="submit" className="rounded-xl bg-violet-600 px-5 py-2 text-sm font-semibold text-white">
                  Publish
                </button>
              </div>
            </form>

            <div className="space-y-3">
              {announcements.map((a) => (
                <div key={a.id} className="glass-card flex items-center justify-between rounded-2xl p-4">
                  <div>
                    <p className="font-semibold text-white">{a.title}</p>
                    <p className="text-sm text-slate-300">{a.message}</p>
                    <span className="text-xs text-violet-300">{a.severity} • {a.isActive ? "Active" : "Paused"}</span>
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={() => updateDoc(doc(db, "announcements", a.id), { isActive: !a.isActive, updatedAt: serverTimestamp() })}
                      className="rounded-xl border border-white/10 px-3 py-1.5 text-xs text-slate-200"
                    >
                      {a.isActive ? "Pause" : "Activate"}
                    </button>
                    <button
                      onClick={() => deleteDoc(doc(db, "announcements", a.id))}
                      className="rounded-xl border border-rose-500/30 p-1.5 text-rose-400"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {tab === "notifications" && (
          <form onSubmit={handleSendPush} className="glass-card max-w-2xl space-y-4 rounded-3xl p-6">
            <h2 className="text-xl font-bold text-white">Push Notification Manager (FCM)</h2>
            <p className="text-xs text-slate-400">
              Dispatches notifications via the role-verified Cloud Function using Firebase Admin SDK.
            </p>
            <input
              type="text"
              placeholder="Topic (e.g. all_users)"
              value={notifTopic}
              onChange={(e) => setNotifTopic(e.target.value)}
              className="w-full rounded-xl border border-white/10 bg-slate-900/70 px-3.5 py-2.5 text-sm text-white"
            />
            <input
              type="text"
              placeholder="Notification Title"
              value={notifTitle}
              onChange={(e) => setNotifTitle(e.target.value)}
              className="w-full rounded-xl border border-white/10 bg-slate-900/70 px-3.5 py-2.5 text-sm text-white"
            />
            <textarea
              placeholder="Notification Body"
              value={notifBody}
              onChange={(e) => setNotifBody(e.target.value)}
              rows={3}
              className="w-full rounded-xl border border-white/10 bg-slate-900/70 px-3.5 py-2.5 text-sm text-white"
            />
            <button type="submit" className="rounded-xl bg-violet-600 px-5 py-2.5 text-sm font-semibold text-white">
              Send Push Notification
            </button>
          </form>
        )}

        {tab === "feedback" && (
          <div className="space-y-3">
            <h2 className="text-xl font-bold text-white">User Feedback ({feedback.length})</h2>
            {feedback.map((item) => (
              <div key={item.id} className="glass-card rounded-2xl p-4">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <span className="rounded-full bg-violet-600/20 px-2.5 py-0.5 text-xs text-violet-300">
                      {item.category} • {item.status}
                    </span>
                    <h4 className="mt-1 font-semibold text-white">{item.subject}</h4>
                    <p className="text-sm text-slate-300">{item.message}</p>
                    <p className="mt-1 text-xs text-slate-400">
                      From {item.userEmail || item.userId} • v{item.appVersion}
                    </p>
                  </div>
                  {item.status !== "resolved" && (
                    <button
                      onClick={() => handleResolveFeedback(item, "Thank you — resolved in our latest update!")}
                      className="rounded-xl bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white"
                    >
                      Mark Resolved
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}

        {tab === "users" && (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-xl font-bold text-white">User Management ({users.length})</h2>
              <input
                type="text"
                placeholder="Search name or email..."
                value={userSearch}
                onChange={(e) => setUserSearch(e.target.value)}
                className="rounded-xl border border-white/10 bg-slate-900/70 px-3.5 py-2 text-sm text-white"
              />
            </div>

            <div className="space-y-2.5">
              {filteredUsers.map((u) => (
                <div key={u.id} className="glass-card flex flex-wrap items-center justify-between gap-3 rounded-2xl p-4">
                  <div>
                    <p className="font-semibold text-white">{u.displayName}</p>
                    <p className="text-xs text-slate-400">
                      {u.email || u.userId} • {u.pdfsCreatedCount} PDFs ({formatBytes(u.totalBytesProcessed)})
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className={`rounded-full px-2.5 py-0.5 text-xs ${u.status === "active" ? "bg-emerald-500/20 text-emerald-300" : "bg-rose-500/20 text-rose-300"}`}>
                      {u.status}
                    </span>
                    <button
                      onClick={() => handleToggleUserStatus(u)}
                      className="rounded-xl border border-white/10 px-3 py-1.5 text-xs text-slate-200 hover:bg-white/5"
                    >
                      {u.status === "suspended" ? "Reactivate" : "Suspend"}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

function StatCard({ title, value, icon }: { title: string; value: string; icon: React.ReactNode }) {
  return (
    <div className="glass-card rounded-2xl p-5">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-slate-400">{title}</span>
        {icon}
      </div>
      <p className="mt-2 text-3xl font-bold text-white">{value}</p>
    </div>
  );
}
