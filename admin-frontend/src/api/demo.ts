import { ApiError } from './errors';
import type {
  Lawyer,
  LawyerKind,
  LoginResponse,
  PendingDocument,
  PendingLawyersResponse,
} from './types';

const base = import.meta.env.BASE_URL.endsWith('/')
  ? import.meta.env.BASE_URL
  : `${import.meta.env.BASE_URL}/`;

const panFront = `${base}demo/pan-front.svg`;
const panBack = `${base}demo/pan-back.svg`;

const now = Date.now();
const hoursAgo = (hours: number): string =>
  new Date(now - hours * 3_600_000).toISOString();

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function pause(): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, 320 + Math.round(Math.random() * 280));
  });
}

function images(): PendingDocument['images'] {
  return [
    { publicId: 'demo-pan-front', secureUrl: panFront, label: 'Front' },
    { publicId: 'demo-pan-back', secureUrl: panBack, label: 'Back' },
  ];
}

const seedDocuments: PendingDocument[] = [
  {
    id: 'doc_pan_01',
    userId: 'usr_8f21c4a7',
    documentType: 'PAN',
    verificationStatus: 'PENDING',
    maskedDocumentNumber: 'XXXXX4821Q',
    panNumber: 'BKRPM4821Q',
    extractedData: {
      name: 'Aarav Mehta',
      fatherName: 'Rajesh Mehta',
      dob: '14/08/1991',
    },
    ocrText: {
      name: 'AARAV MEHTA',
      fatherName: 'RAJESH MEHTA',
      dob: '14/08/1991',
      raw: 'INCOME TAX DEPARTMENT · PERMANENT ACCOUNT NUMBER CARD',
    },
    crossDocumentMismatchFields: ['name', 'dob'],
    requiresManualReview: true,
    manualReviewReason:
      'The PAN holder name and date of birth do not match the signed Aadhaar QR record.',
    failureReason: 'Cross-document comparison returned 2 mismatches.',
    reviewedBy: null,
    reviewedAt: null,
    reviewDecision: null,
    reviewNotes: null,
    images: images(),
    aadhaarQrData: {
      name: 'Aarav S. Mehta',
      dob: '14/08/1992',
      gender: 'Male',
      address: '14, Palm Grove Road, Bandra West, Mumbai',
      pincode: '400050',
    },
    submittedAt: hoursAgo(2),
    verifiedAt: null,
  },
  {
    id: 'doc_pan_02',
    userId: 'usr_2c9d10be',
    documentType: 'PAN',
    verificationStatus: 'PENDING',
    maskedDocumentNumber: 'XXXXX9034M',
    panNumber: 'CKLPI9034M',
    extractedData: {
      name: 'Meera Iyer',
      fatherName: 'Suresh Iyer',
      dob: '02/03/1987',
    },
    ocrText: {
      name: 'MEERA IYER',
      fatherName: 'SURESH IYER',
      dob: '02/03/1987',
      raw: 'INCOME TAX DEPARTMENT · PERMANENT ACCOUNT NUMBER CARD',
    },
    crossDocumentMismatchFields: null,
    requiresManualReview: true,
    manualReviewReason:
      'This PAN number is already linked to another verified account. A duplicate check is required before approval.',
    failureReason: 'Duplicate document number detected.',
    reviewedBy: null,
    reviewedAt: null,
    reviewDecision: null,
    reviewNotes: null,
    images: images(),
    aadhaarQrData: {
      name: 'Meera Iyer',
      dob: '02/03/1987',
      gender: 'Female',
      address: '22, Besant Nagar Beach Road, Chennai',
      pincode: '600090',
    },
    submittedAt: hoursAgo(5),
    verifiedAt: null,
  },
  {
    id: 'doc_pan_03',
    userId: 'usr_71ae44d2',
    documentType: 'PAN',
    verificationStatus: 'PENDING',
    maskedDocumentNumber: 'XXXXX7712L',
    panNumber: 'DMQPD7712L',
    extractedData: {
      name: 'Rohan Das',
      fatherName: 'Anil Das',
      dob: '21/11/1995',
    },
    ocrText: {
      name: 'ROHAN DAS',
      fatherName: 'ANIL DAS',
      dob: '21/11/1995',
      raw: 'INCOME TAX DEPARTMENT · PERMANENT ACCOUNT NUMBER CARD',
    },
    crossDocumentMismatchFields: null,
    requiresManualReview: true,
    manualReviewReason:
      'The signed Aadhaar QR could not be read for this applicant. Verify the PAN details manually or request a new Aadhaar upload.',
    failureReason: null,
    reviewedBy: null,
    reviewedAt: null,
    reviewDecision: null,
    reviewNotes: null,
    images: images(),
    aadhaarQrData: {},
    submittedAt: hoursAgo(9),
    verifiedAt: null,
  },
  {
    id: 'doc_pan_04',
    userId: 'usr_bf63aa19',
    documentType: 'PAN',
    verificationStatus: 'PENDING',
    maskedDocumentNumber: 'XXXXX2290F',
    panNumber: 'EQTPQ2290F',
    extractedData: {
      name: 'Sana Qureshi',
      fatherName: 'Imran Qureshi',
      dob: '09/06/1993',
    },
    ocrText: {
      name: 'SANA QURESHI',
      fatherName: 'IMRAN QURESHI',
      dob: '09/06/1993',
      raw: 'INCOME TAX DEPARTMENT · PERMANENT ACCOUNT NUMBER CARD',
    },
    crossDocumentMismatchFields: ['fatherName'],
    requiresManualReview: true,
    manualReviewReason:
      "The father's name on the PAN does not match the Aadhaar record.",
    failureReason: 'Cross-document comparison returned 1 mismatch.',
    reviewedBy: null,
    reviewedAt: null,
    reviewDecision: null,
    reviewNotes: null,
    images: images(),
    aadhaarQrData: {
      name: 'Sana Qureshi',
      dob: '09/06/1993',
      gender: 'Female',
      address: '7, Model Colony, Shivajinagar, Pune',
      pincode: '411016',
    },
    submittedAt: hoursAgo(14),
    verifiedAt: null,
  },
  {
    id: 'doc_pan_05',
    userId: 'usr_5de0c183',
    documentType: 'PAN',
    verificationStatus: 'PENDING',
    maskedDocumentNumber: 'XXXXX5518H',
    panNumber: 'FRNPN5518H',
    extractedData: {
      name: 'Vikram Nair',
      fatherName: 'Gopal Nair',
      dob: '30/01/1989',
    },
    ocrText: {
      name: 'VIKRAM NAIR',
      fatherName: 'GOPAL NAIR',
      dob: '30/01/1989',
      raw: 'INCOME TAX DEPARTMENT · PERMANENT ACCOUNT NUMBER CARD',
    },
    crossDocumentMismatchFields: null,
    requiresManualReview: true,
    manualReviewReason:
      'OCR confidence for the holder name was below the review threshold. Confirm the printed name against the uploaded card.',
    failureReason: 'OCR confidence below 0.80 for the name field.',
    reviewedBy: null,
    reviewedAt: null,
    reviewDecision: null,
    reviewNotes: null,
    images: images(),
    aadhaarQrData: {
      name: 'Vikram Nair',
      dob: '30/01/1989',
      gender: 'Male',
      address: '41, Panampilly Nagar, Kochi',
      pincode: '682036',
    },
    submittedAt: hoursAgo(19),
    verifiedAt: null,
  },
  {
    id: 'doc_pan_06',
    userId: 'usr_9a1772ef',
    documentType: 'PAN',
    verificationStatus: 'PENDING',
    maskedDocumentNumber: 'XXXXX3374J',
    panNumber: 'GPSPS3374J',
    extractedData: {
      name: 'Priya Sharma',
      fatherName: 'Mohan Sharma',
      dob: '12/05/1990',
    },
    ocrText: {
      name: 'PRIYA SHARMA',
      fatherName: 'MOHAN SHARMA',
      dob: '12/05/1990',
      raw: 'INCOME TAX DEPARTMENT · PERMANENT ACCOUNT NUMBER CARD',
    },
    crossDocumentMismatchFields: ['name', 'pincode'],
    requiresManualReview: true,
    manualReviewReason:
      'The holder name and PIN code do not match the signed Aadhaar QR record.',
    failureReason: 'Cross-document comparison returned 2 mismatches.',
    reviewedBy: null,
    reviewedAt: null,
    reviewDecision: null,
    reviewNotes: null,
    images: images(),
    aadhaarQrData: {
      name: 'Priya M. Sharma',
      dob: '12/05/1990',
      gender: 'Female',
      address: '108, Vaishali Nagar, Jaipur',
      pincode: '302021',
    },
    submittedAt: hoursAgo(22),
    verifiedAt: null,
  },
  {
    id: 'doc_pan_07',
    userId: 'usr_c4e98201',
    documentType: 'PAN',
    verificationStatus: 'PENDING',
    maskedDocumentNumber: 'XXXXX8815C',
    panNumber: 'HTVPV8815C',
    extractedData: {
      name: 'Arjun Verma',
      fatherName: 'Sanjay Verma',
      dob: '07/09/1997',
    },
    ocrText: {
      name: 'ARJUN VERMA',
      fatherName: 'SANJAY VERMA',
      dob: '07/09/1997',
      raw: 'INCOME TAX DEPARTMENT · PERMANENT ACCOUNT NUMBER CARD',
    },
    crossDocumentMismatchFields: null,
    requiresManualReview: true,
    manualReviewReason:
      'No Aadhaar document is linked to this account yet. Use the PAN card fields for a manual identity check.',
    failureReason: null,
    reviewedBy: null,
    reviewedAt: null,
    reviewDecision: null,
    reviewNotes: null,
    images: images(),
    aadhaarQrData: {},
    submittedAt: hoursAgo(27),
    verifiedAt: null,
  },
  {
    id: 'doc_pan_08',
    userId: 'usr_1fb8836d',
    documentType: 'PAN',
    verificationStatus: 'PENDING',
    maskedDocumentNumber: 'XXXXX6642D',
    panNumber: 'JWRPR6642D',
    extractedData: {
      name: 'Nisha Rao',
      fatherName: 'Prakash Rao',
      dob: '18/12/1994',
    },
    ocrText: {
      name: 'NISHA RAO',
      fatherName: 'PRAKASH RAO',
      dob: '18/12/1994',
      raw: 'INCOME TAX DEPARTMENT · PERMANENT ACCOUNT NUMBER CARD',
    },
    crossDocumentMismatchFields: null,
    requiresManualReview: true,
    manualReviewReason:
      'Possible duplicate: this card was previously submitted under a different user account.',
    failureReason: 'Duplicate upload detected.',
    reviewedBy: null,
    reviewedAt: null,
    reviewDecision: null,
    reviewNotes: null,
    images: images(),
    aadhaarQrData: {
      name: 'Nisha Rao',
      dob: '18/12/1994',
      gender: 'Female',
      address: '5, Jubilee Hills, Road No. 36, Hyderabad',
      pincode: '500033',
    },
    submittedAt: hoursAgo(31),
    verifiedAt: null,
  },
];

const seedFreshers: Lawyer[] = [
  {
    id: 'law_fresh_01',
    name: 'Aditi Krishnan',
    email: 'aditi.krishnan@example.com',
    phone: '+91 98450 22190',
    address: 'Indiranagar, Bengaluru, Karnataka',
    university: 'National Law School of India University',
    graduationYear: 2025,
    specialization: 'Constitutional Law',
    barCouncilId: 'KA/2025/11842',
    verificationStatus: 'PENDING',
    rejectionReason: null,
    verifiedAt: null,
    createdAt: hoursAgo(6),
    role: 'LAWYER_FRESHER',
  },
  {
    id: 'law_fresh_02',
    name: 'Kabir Singh',
    email: 'kabir.singh@example.com',
    phone: '+91 98110 77421',
    address: 'Saket, New Delhi',
    university: 'Faculty of Law, University of Delhi',
    graduationYear: 2024,
    specialization: 'Criminal Law',
    barCouncilId: 'DL/2024/09317',
    verificationStatus: 'PENDING',
    rejectionReason: null,
    verifiedAt: null,
    createdAt: hoursAgo(24),
    role: 'LAWYER_FRESHER',
  },
  {
    id: 'law_fresh_03',
    name: 'Ananya Bose',
    email: 'ananya.bose@example.com',
    phone: '+91 98301 55420',
    address: 'Salt Lake, Kolkata, West Bengal',
    university: 'West Bengal National University of Juridical Sciences',
    graduationYear: 2025,
    specialization: 'Corporate Law',
    barCouncilId: 'WB/2025/02774',
    verificationStatus: 'PENDING',
    rejectionReason: null,
    verifiedAt: null,
    createdAt: hoursAgo(48),
    role: 'LAWYER_FRESHER',
  },
  {
    id: 'law_fresh_04',
    name: 'Farhan Ali',
    email: 'farhan.ali@example.com',
    phone: '+91 99490 31875',
    address: 'Banjara Hills, Hyderabad, Telangana',
    university: 'NALSAR University of Law',
    graduationYear: 2023,
    specialization: 'Intellectual Property',
    barCouncilId: 'TS/2023/06551',
    verificationStatus: 'PENDING',
    rejectionReason: null,
    verifiedAt: null,
    createdAt: hoursAgo(72),
    role: 'LAWYER_FRESHER',
  },
];

const seedExperienced: Lawyer[] = [
  {
    id: 'law_exp_01',
    name: 'Radhika Menon',
    email: 'radhika.menon@example.com',
    phone: '+91 98200 44117',
    address: 'Lower Parel, Mumbai, Maharashtra',
    university: 'Government Law College, Mumbai',
    graduationYear: 2009,
    specialization: 'Corporate Law',
    barCouncilId: 'MH/2009/04517',
    practiceAreas: ['Corporate Law', 'Mergers & Acquisitions', 'Private Equity'],
    currentFirm: 'Menon & Associates',
    verificationStatus: 'PENDING',
    rejectionReason: null,
    verifiedAt: null,
    createdAt: hoursAgo(4),
    role: 'LAWYER_EXPERIENCED',
  },
  {
    id: 'law_exp_02',
    name: 'Devansh Rao',
    email: 'devansh.rao@example.com',
    phone: '+91 98860 77203',
    address: 'Koramangala, Bengaluru, Karnataka',
    university: 'National Law School of India University',
    graduationYear: 2013,
    specialization: 'Technology & Data Protection',
    barCouncilId: 'KA/2013/08826',
    practiceAreas: ['Technology Law', 'Data Protection', 'Commercial Contracts'],
    currentFirm: 'Rao Legal',
    verificationStatus: 'PENDING',
    rejectionReason: null,
    verifiedAt: null,
    createdAt: hoursAgo(18),
    role: 'LAWYER_EXPERIENCED',
  },
  {
    id: 'law_exp_03',
    name: 'Leela Varghese',
    email: 'leela.varghese@example.com',
    phone: '+91 94470 66291',
    address: 'Panampilly Nagar, Kochi, Kerala',
    university: 'National University of Advanced Legal Studies',
    graduationYear: 2015,
    specialization: 'Family Law',
    barCouncilId: 'KL/2015/03409',
    practiceAreas: ['Family Law', 'Mediation'],
    currentFirm: 'Varghese Chambers',
    verificationStatus: 'PENDING',
    rejectionReason: null,
    verifiedAt: null,
    createdAt: hoursAgo(48),
    role: 'LAWYER_EXPERIENCED',
  },
];

let documents: PendingDocument[] = clone(seedDocuments);
let freshers: Lawyer[] = clone(seedFreshers);
let experienced: Lawyer[] = clone(seedExperienced);

export const demoApi = {
  async login(password: string): Promise<LoginResponse> {
    await pause();
    if (!password.trim()) {
      throw new ApiError('Enter the admin password to continue.', 401);
    }
    return { success: true, appToken: 'demo-admin-session', role: 'ADMIN' };
  },

  async fetchPendingDocuments(): Promise<PendingDocument[]> {
    await pause();
    return clone(documents);
  },

  async approveDocument(id: string, _notes?: string): Promise<void> {
    await pause();
    const document = documents.find((item) => item.id === id);
    if (!document) {
      throw new ApiError('This document is no longer in the review queue.', 404);
    }
    // Approved records leave the pending feed in the local simulation.
    documents = documents.filter((item) => item.id !== id);
  },

  async rejectDocument(id: string, reason: string): Promise<void> {
    await pause();
    if (!reason.trim()) {
      throw new ApiError('A reason is required to reject a document.', 400);
    }
    if (!documents.some((item) => item.id === id)) {
      throw new ApiError('This document is no longer in the review queue.', 404);
    }
    documents = documents.filter((item) => item.id !== id);
  },

  async fetchPendingLawyers(): Promise<PendingLawyersResponse> {
    await pause();
    return {
      success: true,
      freshers: clone(freshers),
      experienced: clone(experienced),
      totalCount: freshers.length + experienced.length,
    };
  },

  async approveLawyer(kind: LawyerKind, id: string): Promise<void> {
    await pause();
    const list = kind === 'fresher' ? freshers : experienced;
    const profile = list.find((item) => item.id === id);
    if (!profile) {
      throw new ApiError('This lawyer profile could not be found.', 404);
    }
    profile.verificationStatus = 'VERIFIED';
    profile.verifiedAt = Date.now();
    profile.rejectionReason = null;
  },

  async rejectLawyer(kind: LawyerKind, id: string, reason: string): Promise<void> {
    await pause();
    if (!reason.trim()) {
      throw new ApiError('A reason is required to reject a profile.', 400);
    }
    const list = kind === 'fresher' ? freshers : experienced;
    const profile = list.find((item) => item.id === id);
    if (!profile) {
      throw new ApiError('This lawyer profile could not be found.', 404);
    }
    profile.verificationStatus = 'REJECTED';
    profile.rejectionReason = reason.trim();
  },
};
