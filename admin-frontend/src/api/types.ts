export type VerificationStatus =
  | 'PENDING'
  | 'UNDER_VERIFICATION'
  | 'VERIFIED'
  | 'FAILED'
  | 'REJECTED';

export type DocumentType =
  | 'PAN'
  | 'AADHAAR'
  | 'BAR_COUNCIL_ID'
  | 'BAR_CERTIFICATE'
  | 'CERTIFICATE_OF_PRACTICE'
  | 'DEGREE_CERTIFICATE'
  | 'AIBE_CERTIFICATE';

export interface DocumentImage {
  publicId: string;
  secureUrl: string;
  label: string;
}

export interface PendingDocument {
  id: string;
  userId: string;
  documentType: DocumentType;
  verificationStatus: VerificationStatus;
  maskedDocumentNumber: string | null;
  panNumber: string | null;
  extractedData: Record<string, string>;
  ocrText: Record<string, string>;
  crossDocumentMismatchFields: string[] | null;
  requiresManualReview: boolean;
  manualReviewReason: string | null;
  failureReason: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewDecision: string | null;
  reviewNotes: string | null;
  images: DocumentImage[];
  aadhaarQrData: Record<string, string>;
  submittedAt: string | null;
  verifiedAt: string | null;
}

export interface Lawyer {
  id: string;
  name: string;
  email: string;
  phone: string;
  address: string;
  university: string;
  graduationYear: number | string | null;
  specialization: string;
  barCouncilId: string;
  practiceAreas?: string[] | null;
  currentFirm?: string | null;
  verificationStatus: VerificationStatus;
  rejectionReason?: string | null;
  verifiedAt?: number | string | null;
  createdAt?: string | null;
  role?: string;
}

export type LawyerKind = 'fresher' | 'experienced';

export interface PendingLawyersResponse {
  success: boolean;
  freshers: Lawyer[];
  experienced: Lawyer[];
  totalCount: number;
}

export interface LoginResponse {
  success: boolean;
  appToken: string;
  role: string;
}
