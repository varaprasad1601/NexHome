/**
 * The partner profile page's edit dialogs (common/FormModal): for each card its fields, starting values and request
 * body. Profile cards save with PUT /api/partners/profiles/update (partial: a missing field keeps its value, "" clears
 * an optional text, lists are replaced); the onboarding card reviews (PUT /profiles/review); services, documents and
 * bank accounts have their own add / update endpoints. Rules mirror the backend's request records.
 */

import { bankOfIfsc, lookupIfsc } from '../utils/ifscBanks';

const text = (value) => (value ?? '').toString();
const options = (values) => (values ?? []).map((value) => ({ value: value.valueCode, label: value.displayName }));
const numberOrNull = (value) => (String(value).trim() === '' ? null : Number(value));
const listOf = (value) =>
  value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
const check = (pattern, message) => (value) => (value.trim() === '' || pattern.test(value.trim()) ? null : message);

const NAME = /^\s*[\p{L}\p{M}][\p{L}\p{M}\s.'-]*$/u;
const MOBILE = /^[6-9][0-9]{9}$/;
const PIN = /^[1-9][0-9]{5}$/;
const AADHAAR = /^[2-9][0-9]{11}$/;
const DOCUMENT_NUMBER = /^[A-Za-z0-9][A-Za-z0-9 /-]{2,28}[A-Za-z0-9]$/;
const ACCOUNT_NUMBER = /^[0-9]{9,18}$/;
const IFSC = /^[A-Za-z]{4}0[A-Za-z0-9]{6}$/;
const today = () => new Date().toISOString().slice(0, 10);

/** The review decisions staff may take from a status, as the backend's REVIEW_TRANSITIONS (V18). */
export const REVIEW_TRANSITIONS = {
  UNDER_REVIEW: ['APPROVED', 'REJECTED', 'CHANGES_REQUESTED'],
  APPROVED: ['CHANGES_REQUESTED'],
  REJECTED: ['CHANGES_REQUESTED']
};
const REASON_REQUIRED = ['REJECTED', 'CHANGES_REQUESTED'];

/** The document kinds the console takes (DOCUMENT_TYPE codes). */
export const DOCUMENT_KINDS = [
  { value: 'GOVERNMENT_ID', label: 'Government ID' },
  { value: 'CERTIFICATION', label: 'Skill Certificate' }
];

/**
 * A profile card's dialog: { title, fields, initial, body(values) } where body is the partial update.
 *
 * @param ctx { values: the lookups, categories, canEditEmail, user }
 */
export function profileDialog(kind, profile, ctx) {
  const { values } = ctx;
  switch (kind) {
    case 'personal':
      return {
        title: 'Edit Personal Information',
        fields: [
          {
            key: 'firstName',
            label: 'First Name',
            type: 'text',
            required: true,
            maxLength: 80,
            validate: check(NAME, 'Use letters only')
          },
          {
            key: 'middleName',
            label: 'Middle Name',
            type: 'text',
            maxLength: 80,
            validate: check(NAME, 'Use letters only')
          },
          {
            key: 'lastName',
            label: 'Last Name',
            type: 'text',
            required: true,
            maxLength: 80,
            validate: check(NAME, 'Use letters only')
          },
          {
            key: 'aadhaarNumber',
            label: 'Aadhaar Number',
            type: 'text',
            // Always required: the stored number is only shown masked, so it is entered again on each save
            required: true,
            maxLength: 12,
            placeholder: profile.aadhaarNumber ?? '12 digits',
            hint: profile.aadhaarNumber ? 'Enter the full number again to save these details.' : undefined,
            validate: check(AADHAAR, 'Enter the 12-digit Aadhaar number')
          },
          {
            key: 'preferredContactLanguage',
            label: 'Preferred Language',
            type: 'select',
            required: true,
            options: options(values.PREFERRED_CONTACT_LANGUAGE)
          },
          {
            key: 'isAdult',
            label: 'Age 18 or Above',
            type: 'toggle',
            onText: 'Yes, 18 or above',
            offText: 'Below 18 or not confirmed'
          }
        ],
        initial: {
          firstName: text(profile.firstName),
          middleName: text(profile.middleName),
          lastName: text(profile.lastName),
          aadhaarNumber: '',
          preferredContactLanguage: text(profile.preferredContactLanguage),
          isAdult: Boolean(profile.isAdult)
        },
        body: (v) => ({
          firstName: v.firstName.trim(),
          middleName: v.middleName.trim(),
          lastName: v.lastName.trim(),
          // Empty keeps the stored number (an empty string would clear it)
          ...(v.aadhaarNumber.trim() ? { aadhaarNumber: v.aadhaarNumber.trim() } : {}),
          ...(v.preferredContactLanguage ? { preferredContactLanguage: v.preferredContactLanguage } : {}),
          isAdult: v.isAdult
        })
      };
    case 'work':
      return {
        title: 'Edit Work Details',
        fields: [
          {
            key: 'skillCategory',
            label: 'Partner Skill Category',
            required: true,
            type: 'select',
            options: options(values.SKILL_CATEGORY)
          },
          {
            key: 'mainServiceCategoryId',
            label: 'Main Service',
            required: true,
            type: 'select',
            options: ctx.categories
              .filter((category) => category.isActive)
              .map((category) => ({ value: String(category.serviceCategoryId), label: category.name }))
          },
          {
            key: 'yearsExperience',
            label: 'Experience (years)',
            required: true,
            type: 'number',
            min: 0,
            validate: (value) => (value !== '' && (Number(value) < 0 || Number(value) > 60) ? 'Between 0 and 60' : null)
          },
          {
            key: 'preferredArrangement',
            label: 'Preferred Arrangement',
            required: true,
            type: 'select',
            options: options(values.PREFERRED_ARRANGEMENT)
          },
          {
            key: 'paymentBasis',
            label: 'Payment Basis',
            required: true,
            type: 'select',
            options: options(values.PAYMENT_BASIS)
          },
          { key: 'expectedEarningsInr', label: 'Expected Earnings', required: true, type: 'money' },
          { key: 'visitChargeInr', label: 'Visit Charge', required: true, type: 'money' },
          { key: 'minimumJobPaymentInr', label: 'Minimum Job Payment', required: true, type: 'money' },
          {
            // Stored in additional_skills as the categories' names
            key: 'additionalSkills',
            label: 'Additional Service Categories',
            type: 'multiselect',
            wide: true,
            placeholder: 'Choose other categories the partner works in',
            // Every active category but the main service chosen above
            options: (v) =>
              [
                ...ctx.categories
                  .filter(
                    (category) => category.isActive && String(category.serviceCategoryId) !== v.mainServiceCategoryId
                  )
                  .map((category) => category.name),
                // Earlier free-text entries stay choosable until removed
                ...(profile.additionalSkills ?? []).filter(
                  (name) => !ctx.categories.some((category) => category.name === name)
                )
              ].map((name) => ({ value: name, label: name }))
          },
          { key: 'previousWorkDetails', label: 'Previous Work', type: 'textarea', maxLength: 2000, rows: 3 }
        ],
        initial: {
          skillCategory: text(profile.skillCategory),
          mainServiceCategoryId: profile.mainServiceCategoryId ? String(profile.mainServiceCategoryId) : '',
          yearsExperience: text(profile.yearsExperience),
          preferredArrangement: text(profile.preferredArrangement),
          paymentBasis: text(profile.paymentBasis),
          expectedEarningsInr: text(profile.expectedEarningsInr),
          visitChargeInr: text(profile.visitChargeInr),
          minimumJobPaymentInr: text(profile.minimumJobPaymentInr),
          additionalSkills: profile.additionalSkills ?? [],
          previousWorkDetails: text(profile.previousWorkDetails)
        },
        body: (v) => ({
          ...(v.skillCategory ? { skillCategory: v.skillCategory } : {}),
          ...(v.mainServiceCategoryId ? { mainServiceCategoryId: Number(v.mainServiceCategoryId) } : {}),
          ...(v.preferredArrangement ? { preferredArrangement: v.preferredArrangement } : {}),
          ...(v.paymentBasis ? { paymentBasis: v.paymentBasis } : {}),
          yearsExperience: numberOrNull(v.yearsExperience),
          expectedEarningsInr: numberOrNull(v.expectedEarningsInr),
          visitChargeInr: numberOrNull(v.visitChargeInr),
          minimumJobPaymentInr: numberOrNull(v.minimumJobPaymentInr),
          // The main service is never also an additional one
          additionalSkills: v.additionalSkills.filter(
            (name) =>
              name !==
              ctx.categories.find((category) => String(category.serviceCategoryId) === v.mainServiceCategoryId)?.name
          ),
          previousWorkDetails: v.previousWorkDetails.trim()
        })
      };
    case 'contact':
      return {
        title: 'Edit Contact Details',
        fields: [
          {
            key: 'alternateContactNumber',
            label: 'Alternate Number',
            type: 'text',
            maxLength: 10,
            validate: check(MOBILE, 'Enter a 10-digit mobile number')
          },
          ...(ctx.canEditEmail
            ? [
                {
                  key: 'email',
                  label: 'Email Address',
                  type: 'text',
                  maxLength: 255,
                  validate: check(/^[^\s@]+@[^\s@]+\.[^\s@]+$/, 'Enter a valid email address')
                }
              ]
            : [])
        ],
        initial: { alternateContactNumber: text(profile.alternateContactNumber), email: text(ctx.user.email) },
        body: (v) => ({ alternateContactNumber: v.alternateContactNumber.trim() })
      };
    case 'address':
      return {
        title: 'Edit Address',
        fields: [
          { key: 'addressLine1', label: 'Address Line 1', type: 'text', required: true, maxLength: 300, wide: true },
          { key: 'addressLine2', label: 'Address Line 2', type: 'text', maxLength: 300, wide: true },
          { key: 'landmark', label: 'Landmark', type: 'text', maxLength: 150 },
          { key: 'locality', label: 'Locality', type: 'text', required: true, maxLength: 100 },
          { key: 'city', label: 'City', type: 'text', required: true, maxLength: 100 },
          { key: 'state', label: 'State', type: 'text', required: true, maxLength: 100 },
          {
            key: 'postalCode',
            label: 'PIN Code',
            type: 'text',
            required: true,
            maxLength: 6,
            validate: check(PIN, 'Enter a 6-digit PIN code')
          }
        ],
        initial: {
          addressLine1: text(profile.addressLine1),
          addressLine2: text(profile.addressLine2),
          landmark: text(profile.landmark),
          locality: text(profile.locality),
          city: text(profile.city),
          state: text(profile.state),
          postalCode: text(profile.postalCode)
        },
        body: (v) => ({
          addressLine1: v.addressLine1.trim(),
          addressLine2: v.addressLine2.trim(),
          landmark: v.landmark.trim(),
          locality: v.locality.trim(),
          city: v.city.trim(),
          state: v.state.trim(),
          postalCode: v.postalCode.trim()
        })
      };
    case 'emergency':
      return {
        title: 'Edit Emergency Contact',
        fields: [
          { key: 'emergencyContactName', label: 'Name', type: 'text', required: true, maxLength: 120 },
          {
            key: 'emergencyContactRelationship',
            label: 'Relationship',
            type: 'select',
            required: true,
            options: options(values.EMERGENCY_CONTACT_RELATIONSHIP)
          },
          {
            key: 'emergencyContactNumber',
            label: 'Number',
            type: 'text',
            required: true,
            maxLength: 10,
            validate: check(MOBILE, 'Enter a 10-digit mobile number')
          }
        ],
        initial: {
          emergencyContactName: text(profile.emergencyContactName),
          emergencyContactRelationship: text(profile.emergencyContactRelationship),
          emergencyContactNumber: text(profile.emergencyContactNumber)
        },
        body: (v) => ({
          emergencyContactName: v.emergencyContactName.trim(),
          emergencyContactRelationship: v.emergencyContactRelationship,
          emergencyContactNumber: v.emergencyContactNumber.trim()
        })
      };
    case 'coverage':
      return {
        title: 'Edit Coverage',
        fields: [
          {
            key: 'serviceAreas',
            label: 'Service Areas',
            type: 'text',
            required: true,
            wide: true,
            hint: 'Separate areas with commas.'
          },
          {
            key: 'maxTravelKm',
            label: 'Travels Up To (km)',
            type: 'number',
            required: true,
            min: 0,
            validate: (value) => (value !== '' && Number(value) <= 0 ? 'More than 0 km' : null)
          },
          {
            key: 'weekendAvailability',
            label: 'Weekends',
            required: true,
            type: 'select',
            options: options(values.WEEKEND_AVAILABILITY)
          }
        ],
        initial: {
          serviceAreas: (profile.serviceAreas ?? []).join(', '),
          maxTravelKm: text(profile.maxTravelKm),
          weekendAvailability: text(profile.weekendAvailability)
        },
        body: (v) => ({
          serviceAreas: listOf(v.serviceAreas),
          maxTravelKm: numberOrNull(v.maxTravelKm),
          ...(v.weekendAvailability ? { weekendAvailability: v.weekendAvailability } : {})
        })
      };
    case 'review': {
      const targets = REVIEW_TRANSITIONS[profile.onboardingStatus] ?? [];
      const label = (code) =>
        values.PARTNER_ONBOARDING_STATUS?.find((value) => value.valueCode === code)?.displayName ?? code;
      return {
        title: 'Review Onboarding',
        fields: [
          {
            key: 'onboardingStatus',
            label: 'Decision',
            type: 'select',
            required: true,
            wide: true,
            options: targets.map((code) => ({ value: code, label: label(code) }))
          },
          {
            key: 'rejectionReason',
            label: 'Reason (shown to the partner)',
            type: 'textarea',
            maxLength: 2000,
            rows: 3,
            validate: (value, all) =>
              REASON_REQUIRED.includes(all.onboardingStatus) && !value.trim() ? 'Say what the partner should do' : null
          }
        ],
        initial: { onboardingStatus: targets[0] ?? '', rejectionReason: '' },
        body: (v) => ({ onboardingStatus: v.onboardingStatus, rejectionReason: v.rejectionReason.trim() || null })
      };
    }
    default:
      return null;
  }
}

/** Add / edit a service the partner offers; the service itself is fixed once added. */
export function serviceDialog(item, catalogue, offered) {
  const taken = new Set(offered.map((service) => service.serviceId));
  return {
    title: item ? `Edit ${item.serviceName}` : 'Add Service',
    fields: [
      ...(item
        ? []
        : [
            {
              key: 'serviceId',
              label: 'Service',
              type: 'select',
              required: true,
              wide: true,
              options: catalogue
                .filter((service) => !taken.has(service.serviceId))
                .map((service) => ({
                  value: String(service.serviceId),
                  label: `${service.name} · ${service.categoryName}`
                }))
            }
          ]),
      { key: 'experienceYears', label: 'Experience (years)', type: 'number', min: 0 },
      { key: 'isPrimaryService', label: 'Primary Service', type: 'toggle', onText: 'Primary', offText: 'Not primary' },
      ...(item ? [{ key: 'isActive', label: 'Status', type: 'toggle', onText: 'Active', offText: 'Inactive' }] : [])
    ],
    initial: {
      serviceId: '',
      experienceYears: text(item?.experienceYears),
      isPrimaryService: item ? item.isPrimaryService : offered.length === 0,
      isActive: item?.isActive ?? true
    },
    body: (v) => ({
      ...(item ? {} : { serviceId: Number(v.serviceId) }),
      experienceYears: numberOrNull(v.experienceYears),
      isPrimaryService: v.isPrimaryService,
      ...(item ? { isActive: v.isActive } : {})
    })
  };
}

/**
 * Add / edit a document: a Government ID (its type from GOVERNMENT_ID_TYPE: Aadhaar Card, PAN Card, ...) or a Skill
 * Certificate (its name), with the number and an optional expiry date. The kind is fixed once added; the stored
 * number is only shown masked, so on edit an empty number keeps it.
 */
export function documentDialog(item, values, kind = item?.documentType) {
  const noun = DOCUMENT_KINDS.find((option) => option.value === kind)?.label ?? 'Document';
  return {
    title: `${item ? 'Edit' : 'Add'} ${noun}`,
    fields: [
      // The kind is fixed once added, and when adding from its section
      ...(item || kind
        ? []
        : [{ key: 'documentType', label: 'Document', type: 'select', required: true, options: DOCUMENT_KINDS }]),
      {
        key: 'idType',
        label: 'ID Type',
        type: 'select',
        required: true,
        options: options(values.GOVERNMENT_ID_TYPE),
        show: (v) => v.documentType === 'GOVERNMENT_ID'
      },
      {
        key: 'documentName',
        label: 'Certificate Name',
        type: 'text',
        required: true,
        maxLength: 150,
        placeholder: 'e.g. ITI Electrician',
        show: (v) => v.documentType === 'CERTIFICATION'
      },
      {
        key: 'documentNumber',
        label: 'Number',
        type: 'text',
        required: !item,
        maxLength: 30,
        placeholder: item?.documentNumber ?? undefined,
        hint: item ? 'Leave empty to keep the current number.' : undefined,
        validate: check(DOCUMENT_NUMBER, '4 to 30 letters, digits, spaces, / or -')
      },
      { key: 'expiryDate', label: 'Expiry Date', type: 'date', min: today() }
    ],
    initial: {
      documentType: item?.documentType ?? kind ?? 'GOVERNMENT_ID',
      idType: text(item?.idType),
      documentName: text(item?.documentName),
      documentNumber: '',
      expiryDate: text(item?.expiryDate)
    },
    body: (v) => ({
      ...(item ? {} : { documentType: v.documentType }),
      ...(v.documentType === 'GOVERNMENT_ID' ? { idType: v.idType } : {}),
      ...(v.documentType === 'CERTIFICATION' ? { documentName: v.documentName.trim() } : {}),
      ...(v.documentNumber.trim() ? { documentNumber: v.documentNumber.trim() } : {}),
      ...(v.expiryDate ? { expiryDate: v.expiryDate } : {})
    })
  };
}

/** Add / edit a bank account; the account number and IFSC are fixed once added (and never shown in full). */
export function bankDialog(item) {
  return {
    title: item ? 'Edit Bank Account' : 'Add Bank Account',
    fields: [
      {
        key: 'accountHolderName',
        label: 'Account Holder Name',
        type: 'text',
        required: true,
        maxLength: 120,
        wide: true
      },
      ...(item
        ? []
        : [
            {
              key: 'accountNumber',
              label: 'Account Number',
              type: 'text',
              required: true,
              maxLength: 18,
              validate: check(ACCOUNT_NUMBER, '9 to 18 digits')
            },
            {
              key: 'ifscCode',
              label: 'IFSC Code',
              type: 'text',
              required: true,
              maxLength: 11,
              validate: check(IFSC, 'e.g. SBIN0001234')
            }
          ]),
      {
        key: 'bankName',
        label: 'Bank Name',
        type: 'text',
        required: true,
        maxLength: 120,
        hint: item ? undefined : 'Filled in from the IFSC code; you can change it.',
        // At once from the offline list, until typed in by hand; the IFSC lookup below may refine it
        derive: item ? undefined : (v) => bankOfIfsc(v.ifscCode) ?? v.bankName
      },
      {
        key: 'branchName',
        label: 'Branch',
        type: 'text',
        required: true,
        maxLength: 150,
        hint: item ? undefined : 'Filled in from the IFSC code when it can be looked up; else type it in.'
      }
    ],
    initial: {
      accountHolderName: text(item?.accountHolderName),
      accountNumber: '',
      ifscCode: '',
      bankName: text(item?.bankName),
      branchName: text(item?.branchName)
    },
    // A new account's bank and branch from its IFSC code (Razorpay's public IFSC service)
    lookup: item ? null : { watch: 'ifscCode', run: lookupIfsc },
    // The first account becomes the primary one by itself (backend), so the form has no primary option
    body: (v) => ({
      accountHolderName: v.accountHolderName.trim(),
      ...(item ? {} : { accountNumber: v.accountNumber.trim(), ifscCode: v.ifscCode.trim().toUpperCase() }),
      bankName: v.bankName.trim(),
      branchName: v.branchName.trim()
    })
  };
}
