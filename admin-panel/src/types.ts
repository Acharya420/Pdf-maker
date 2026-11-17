import type { Timestamp } from "firebase/firestore";

export interface UserProfile {
  id: string;
  userId: string;
  displayName: string;
  email?: string;
  photoUrl?: string;
  preferredTheme: "system" | "light" | "dark";
  preferredLanguage: string;
  pdfsCreatedCount: number;
  totalBytesProcessed: number;
  status: "active" | "suspended";
  lastActiveAt?: Timestamp;
  createdAt?: Timestamp;
  updatedAt?: Timestamp;
}

export interface AppGlobalConfig {
  maintenanceMode: boolean;
  maintenanceMessage: string;
  latestVersionCode: number;
  latestVersionName: string;
  minSupportedVersionCode: number;
  forceUpdate: boolean;
  releaseNotes: string;
  enableImageToPdf: boolean;
  enableCompress: boolean;
  enableMerge: boolean;
  enableSplit: boolean;
  enableOrganizer: boolean;
  enablePasswordProtect: boolean;
}

export const DEFAULT_GLOBAL_CONFIG: AppGlobalConfig = {
  maintenanceMode: false,
  maintenanceMessage: "Scheduled cloud maintenance in progress. Offline PDF tools remain available.",
  latestVersionCode: 1,
  latestVersionName: "1.0",
  minSupportedVersionCode: 1,
  forceUpdate: false,
  releaseNotes: "Initial Liquid Glass PDF Maker Studio release.",
  enableImageToPdf: true,
  enableCompress: true,
  enableMerge: true,
  enableSplit: true,
  enableOrganizer: true,
  enablePasswordProtect: true,
};

export interface Announcement {
  id: string;
  title: string;
  message: string;
  severity: "info" | "warning" | "update";
  isActive: boolean;
  createdBy: string;
  createdAt?: Timestamp;
}

export interface UserFeedback {
  id: string;
  userId: string;
  userEmail?: string;
  category: "bug" | "feature" | "general";
  subject: string;
  message: string;
  appVersion: string;
  status: "open" | "in_review" | "resolved";
  adminReply?: string;
  createdAt?: Timestamp;
}
