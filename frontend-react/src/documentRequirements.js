export const DOCUMENT_REQUIREMENTS = [
  {
    category: "REGISTRATION_DOC",
    label: "Organisation registration document",
    required: true,
    examples: "ACRA BizFile or Registry of Societies (ROS) certificate.",
    aiRule: "Registration evidence should match organisation identity and legal entity type.",
  },
  {
    category: "FLOOR_PLAN",
    label: "Revised floor plan (ECDC/Childminding)",
    required: false,
    examples: "Drawn-to-scale indoor/outdoor layout showing play space and exits.",
    aiRule: "Plan/blueprint naming and technical file type should align with layout evidence.",
  },
  {
    category: "CCTV_AND_SAFETY_PROOF",
    label: "CCTV / remedial safety proof (ECDC)",
    required: false,
    examples: "Proof of CCTV installation, hazardous-material removal, or safety remediation works.",
    aiRule: "Should indicate concrete safety measures and supporting installation/remediation evidence.",
  },
  {
    category: "ATTENDANCE_LOG",
    label: "Attendance logs (SCFA)",
    required: false,
    examples: "Student attendance records used to support subsidy claims for audited months.",
    aiRule: "Attendance entries should correspond to claim period context and SCC operations.",
  },
  {
    category: "SUBSIDY_WITHDRAWAL_FORM",
    label: "Subsidy withdrawal / claims document (SCFA)",
    required: false,
    examples: "Updated subsidy withdrawal forms or claim-adjustment forms.",
    aiRule: "Should indicate subsidy administration context and relevant claim period.",
  },
  {
    category: "ENVIRONMENT_COMPLIANCE_RECORD",
    label: "Environment compliance record (SCFA)",
    required: false,
    examples: "Inspection readings or reports for lighting (e.g., 300 lux) and ventilation.",
    aiRule: "Should reflect physical environment checks linked to SCC standards.",
  },
  {
    category: "STAFF_ROSTER",
    label: "Staff duty roster (HFAA)",
    required: false,
    examples: "On-duty staffing roster for verifying staff-to-resident ratio.",
    aiRule: "Should provide traceable staffing allocation for inspection periods.",
  },
  {
    category: "SANITATION_AND_FIRE_CERT",
    label: "Sanitation / fire safety evidence (HFAA)",
    required: false,
    examples: "Updated fire safety certificates, sanitation reports, or hygiene remedial records.",
    aiRule: "Should indicate safe and sanitary premises with dated compliance artifacts.",
  },
  {
    category: "STAFF_MEDICAL_SCREENING",
    label: "Staff medical screening proof (HFAA)",
    required: false,
    examples: "Medical screening documents for staff assigned to resident-care duties.",
    aiRule: "Should evidence staff fitness/screening in care environment context.",
  },
  {
    category: "HOME_SAFETY_PHOTOS",
    label: "Home safety photos (Childminding)",
    required: false,
    examples: "Photos of child-proofing, safety gates, and infant-safe setup in residence.",
    aiRule: "Should clearly represent home safety controls and equipment.",
  },
  {
    category: "EQUIPMENT_INVENTORY",
    label: "Infant equipment inventory (Childminding)",
    required: false,
    examples: "Updated inventory of infant care equipment and capacity-related resources.",
    aiRule: "Should align with declared infant capacity and home assessment context.",
  },
  {
    category: "CAPACITY_PLAN",
    label: "Capacity declaration plan (Childminding/ECDC)",
    required: false,
    examples: "Document declaring maximum child/infant capacity with space allocation rationale.",
    aiRule: "Should indicate safe accommodation capacity with space justification.",
  },
  {
    category: "GENERAL_SUPPORTING",
    label: "General supporting document",
    required: false,
    examples: "Other supporting evidence not covered by the above categories.",
    aiRule: "General metadata sanity check.",
  },
];

export const REQUIRED_DOCUMENT_CATEGORIES = DOCUMENT_REQUIREMENTS
  .filter((d) => d.required)
  .map((d) => d.category);
