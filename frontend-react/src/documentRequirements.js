export const TRACK_OPTIONS = [
  { value: "ECDC", label: "Early Childhood Development Centre (ECDC)" },
  { value: "SCFA", label: "Student Care Financial Assistance (SCFA)" },
  { value: "HFAA", label: "Home for the Aged (HFAA)" },
  { value: "CHILDMINDING", label: "Childminding Pilot" },
];

const COMMON = [
  {
    category: "REGISTRATION_DOC",
    label: "Organisation registration document",
    required: true,
    examples: "ACRA BizFile or Registry of Societies (ROS) certificate.",
    aiRule: "Registration evidence should match organisation identity and legal entity type.",
  },
  {
    category: "GENERAL_SUPPORTING",
    label: "General supporting document",
    required: false,
    examples: "Other supporting evidence not covered by the selected track.",
    aiRule: "General metadata sanity check.",
  },
];

export const DOCUMENT_REQUIREMENTS_BY_TRACK = {
  ECDC: [
    {
      category: "FLOOR_PLAN",
      label: "Revised floor plan",
      required: true,
      examples: "Drawn-to-scale indoor/outdoor layout showing play space and exits.",
      aiRule: "Plan/blueprint naming and file type should align with ECDC layout evidence.",
    },
    {
      category: "CCTV_AND_SAFETY_PROOF",
      label: "CCTV / remedial safety proof",
      required: false,
      examples: "Proof of CCTV installation, hazardous-material removal, or safety remediation works.",
      aiRule: "Should indicate concrete safety measures and supporting remediation evidence.",
    },
    {
      category: "CAPACITY_PLAN",
      label: "Capacity declaration plan",
      required: false,
      examples: "Document declaring child capacity with space allocation rationale.",
      aiRule: "Should indicate safe accommodation capacity with space justification.",
    },
  ],
  SCFA: [
    {
      category: "ATTENDANCE_LOG",
      label: "Attendance logs",
      required: true,
      examples: "Student attendance records supporting subsidy claims for audited months.",
      aiRule: "Attendance entries should correspond to claim-period context and SCC operations.",
    },
    {
      category: "SUBSIDY_WITHDRAWAL_FORM",
      label: "Subsidy withdrawal / claims document",
      required: false,
      examples: "Updated subsidy withdrawal forms or claim-adjustment forms.",
      aiRule: "Should indicate subsidy administration context and relevant claim period.",
    },
    {
      category: "ENVIRONMENT_COMPLIANCE_RECORD",
      label: "Environment compliance record",
      required: false,
      examples: "Inspection readings/reports for lighting (e.g., 300 lux) and ventilation.",
      aiRule: "Should reflect physical environment checks linked to SCC standards.",
    },
  ],
  HFAA: [
    {
      category: "STAFF_ROSTER",
      label: "Staff duty roster",
      required: true,
      examples: "On-duty staffing roster for staff-to-resident ratio verification.",
      aiRule: "Should provide traceable staffing allocation for inspection periods.",
    },
    {
      category: "SANITATION_AND_FIRE_CERT",
      label: "Sanitation / fire safety evidence",
      required: false,
      examples: "Updated fire certs, sanitation reports, or hygiene remedial records.",
      aiRule: "Should indicate safe and sanitary premises with dated compliance artifacts.",
    },
    {
      category: "STAFF_MEDICAL_SCREENING",
      label: "Staff medical screening proof",
      required: false,
      examples: "Medical screening evidence for staff in resident-care duties.",
      aiRule: "Should evidence staff fitness/screening in care-environment context.",
    },
  ],
  CHILDMINDING: [
    {
      category: "HOME_SAFETY_PHOTOS",
      label: "Home safety photos",
      required: true,
      examples: "Photos of child-proofing, safety gates, and infant-safe setup in residence.",
      aiRule: "Should clearly represent home safety controls and equipment.",
    },
    {
      category: "EQUIPMENT_INVENTORY",
      label: "Infant equipment inventory",
      required: false,
      examples: "Updated inventory of infant-care equipment and capacity resources.",
      aiRule: "Should align with declared infant capacity and home-assessment context.",
    },
    {
      category: "CAPACITY_PLAN",
      label: "Capacity declaration plan",
      required: false,
      examples: "Document declaring infant capacity with space allocation rationale.",
      aiRule: "Should indicate safe accommodation capacity with space justification.",
    },
  ],
};

export function requirementsForTrack(track) {
  return [...(DOCUMENT_REQUIREMENTS_BY_TRACK[track] || []), ...COMMON];
}

export function requiredCategoriesForTrack(track) {
  return requirementsForTrack(track).filter((d) => d.required).map((d) => d.category);
}
