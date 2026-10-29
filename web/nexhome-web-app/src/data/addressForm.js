const PIN_CODE = /^[1-9][0-9]{5}$/;

/**
 * The Add / Edit Address dialog's fields (FormModal): what the customer app asks for, India only (6-digit PIN).
 * Used by the customer profile and by Create Booking for a customer without an address.
 */
export function addressFields(addressTypes) {
  return [
    { key: 'contactName', label: 'Contact Name', type: 'text', required: true, maxLength: 150 },
    {
      key: 'mobileNumber',
      label: 'Mobile Number',
      type: 'text',
      required: true,
      maxLength: 15,
      validate: (value) => (/^[0-9]{6,15}$/.test(value.trim()) ? null : 'Enter 6 to 15 digits')
    },
    {
      key: 'addressType',
      label: 'Address Type',
      type: 'select',
      required: true,
      options: addressTypes.map((value) => ({ value: value.valueCode, label: value.displayName }))
    },
    { key: 'label', label: 'Label', type: 'text', maxLength: 50, placeholder: 'e.g. Parents house' },
    { key: 'addressLine1', label: 'Address Line 1', type: 'text', required: true, maxLength: 255, wide: true },
    { key: 'addressLine2', label: 'Address Line 2', type: 'text', maxLength: 255, wide: true },
    { key: 'landmark', label: 'Landmark', type: 'text', maxLength: 255 },
    { key: 'city', label: 'City', type: 'text', required: true, maxLength: 100 },
    { key: 'state', label: 'State', type: 'text', required: true, maxLength: 100 },
    {
      key: 'postalCode',
      label: 'PIN Code',
      type: 'text',
      required: true,
      maxLength: 6,
      validate: (value) => (PIN_CODE.test(value.trim()) ? null : 'Enter a 6-digit PIN code')
    },
    {
      key: 'isDefault',
      label: 'Default Address',
      type: 'toggle',
      onText: 'Default for bookings',
      offText: 'Not the default'
    }
  ];
}

/** An empty dialog for a new address, with the customer's name and mobile filled in. */
export function newAddressValues(contactName, mobileNumber, addressTypes) {
  return {
    contactName: contactName ?? '',
    mobileNumber: mobileNumber ?? '',
    addressType: addressTypes[0]?.valueCode ?? '',
    label: '',
    addressLine1: '',
    addressLine2: '',
    landmark: '',
    city: '',
    state: '',
    postalCode: '',
    isDefault: false
  };
}

/**
 * The request fields of the dialog's values. On update `""` clears an optional field; on create it is left out
 * (null).
 */
export function addressBody(values, editing) {
  const text = (value) => value.trim() || (editing ? '' : null);
  return {
    contactName: values.contactName.trim(),
    mobileNumber: values.mobileNumber.trim(),
    addressType: values.addressType,
    label: text(values.label),
    addressLine1: values.addressLine1.trim(),
    addressLine2: text(values.addressLine2),
    landmark: text(values.landmark),
    city: values.city.trim(),
    state: values.state.trim(),
    postalCode: values.postalCode.trim()
  };
}
