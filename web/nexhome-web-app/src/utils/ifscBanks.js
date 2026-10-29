/**
 * The bank of an IFSC code: its first four letters name the bank (RBI's IFSC format: 4 letters, 0, then 6 for the
 * branch). The list covers the public, private, small finance and payments banks and the larger foreign and
 * co-operative ones; an unknown code gives null. Offline on purpose: no outside lookup service.
 */
const BANKS = {
  // Public sector
  SBIN: 'State Bank of India',
  PUNB: 'Punjab National Bank',
  BARB: 'Bank of Baroda',
  CNRB: 'Canara Bank',
  UBIN: 'Union Bank of India',
  BKID: 'Bank of India',
  IDIB: 'Indian Bank',
  CBIN: 'Central Bank of India',
  IOBA: 'Indian Overseas Bank',
  UCBA: 'UCO Bank',
  MAHB: 'Bank of Maharashtra',
  PSIB: 'Punjab & Sind Bank',
  // Private sector
  HDFC: 'HDFC Bank',
  ICIC: 'ICICI Bank',
  UTIB: 'Axis Bank',
  KKBK: 'Kotak Mahindra Bank',
  INDB: 'IndusInd Bank',
  YESB: 'Yes Bank',
  IDFB: 'IDFC First Bank',
  FDRL: 'Federal Bank',
  SIBL: 'South Indian Bank',
  KARB: 'Karnataka Bank',
  KVBL: 'Karur Vysya Bank',
  CIUB: 'City Union Bank',
  TMBL: 'Tamilnad Mercantile Bank',
  LAVB: 'Lakshmi Vilas Bank',
  DLXB: 'Dhanlaxmi Bank',
  CSBK: 'CSB Bank',
  RATN: 'RBL Bank',
  JAKA: 'Jammu & Kashmir Bank',
  NTBL: 'Nainital Bank',
  BDBL: 'Bandhan Bank',
  IBKL: 'IDBI Bank',
  // Small finance and payments banks
  AUBL: 'AU Small Finance Bank',
  ESFB: 'Equitas Small Finance Bank',
  UJVN: 'Ujjivan Small Finance Bank',
  SURY: 'Suryoday Small Finance Bank',
  JSFB: 'Jana Small Finance Bank',
  FSFB: 'Fincare Small Finance Bank',
  ESMF: 'ESAF Small Finance Bank',
  UTKS: 'Utkarsh Small Finance Bank',
  NESF: 'North East Small Finance Bank',
  CAPS: 'Capital Small Finance Bank',
  SMCB: 'Shivalik Small Finance Bank',
  UNTY: 'Unity Small Finance Bank',
  PYTM: 'Paytm Payments Bank',
  AIRP: 'Airtel Payments Bank',
  IPOS: 'India Post Payments Bank',
  FINO: 'Fino Payments Bank',
  JIOP: 'Jio Payments Bank',
  NSPB: 'NSDL Payments Bank',
  // Foreign and others
  HSBC: 'HSBC',
  SCBL: 'Standard Chartered Bank',
  CITI: 'Citibank',
  DBSS: 'DBS Bank India',
  DEUT: 'Deutsche Bank',
  BARC: 'Barclays Bank',
  APGB: 'Andhra Pragathi Grameena Bank',
  APGV: 'Andhra Pradesh Grameena Vikas Bank',
  TGMB: 'Telangana Grameena Bank',
  SVCB: 'SVC Co-operative Bank',
  SRCB: 'Saraswat Co-operative Bank',
  COSB: 'Cosmos Co-operative Bank',
  APMC: 'Andhra Pradesh State Co-operative Bank',
  KSCB: 'Karnataka State Co-operative Apex Bank',
  TNSC: 'Tamil Nadu State Apex Co-operative Bank'
};

/** The bank name of a complete IFSC code (e.g. SBIN0001234), or null when it is incomplete or not on the list. */
export function bankOfIfsc(ifsc) {
  const code = (ifsc ?? '').trim().toUpperCase();
  if (!/^[A-Z]{4}0[A-Z0-9]{6}$/.test(code)) return null;
  return BANKS[code.slice(0, 4)] ?? null;
}

/**
 * The bank and branch of a complete IFSC code from Razorpay's public IFSC service (https://ifsc.razorpay.com), with
 * the bank from the list above when the service cannot be reached or does not know the code: { bankName, branchName }
 * (branchName null then), or null for an incomplete code.
 */
export async function lookupIfsc(ifsc) {
  const code = (ifsc ?? '').trim().toUpperCase();
  if (!/^[A-Z]{4}0[A-Z0-9]{6}$/.test(code)) return null;
  const offline = { bankName: bankOfIfsc(code), branchName: null };
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 6000);
  try {
    const response = await fetch(`https://ifsc.razorpay.com/${code}`, { signal: controller.signal });
    if (!response.ok) return offline;
    const data = await response.json();
    return { bankName: data.BANK || offline.bankName, branchName: data.BRANCH || null };
  } catch {
    return offline;
  } finally {
    clearTimeout(timer);
  }
}
