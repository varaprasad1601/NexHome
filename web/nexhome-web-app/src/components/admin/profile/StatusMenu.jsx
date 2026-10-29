import { useState } from 'react';

import RowActions from '../system/RowActions';
import { useAdminAuth } from '../../../context/adminAuth';
import { accountStatusLabel } from '../../../data/staffOptions';

/** The status changes on offer, by current status: the same as the tables' row menus. */
const STATUS_ACTIONS = {
  ACTIVE: [
    { status: 'DEACTIVATED', label: 'Deactivate' },
    { status: 'SUSPENDED', label: 'Suspend', danger: true }
  ],
  DEACTIVATED: [
    { status: 'ACTIVE', label: 'Activate' },
    { status: 'SUSPENDED', label: 'Suspend', danger: true }
  ],
  SUSPENDED: [{ status: 'ACTIVE', label: 'Activate' }],
  BLOCKED: [{ status: 'ACTIVE', label: 'Activate' }]
};

/**
 * The "..." account status menu of a profile page's summary (Deactivate, Suspend, Activate as in the lists), saved at
 * once with PUT /api/users/status/update. The page shows it only to those who may change the account (the lists'
 * write permission) and never on one's own account. `onChanged(status)` updates the page; `onMessage(text, tone)`
 * shows the result. The trigger is the bare ⋮ icon (no word, border or background), at the top of the summary.
 */
export default function StatusMenu({ user, name, onChanged, onMessage }) {
  const { authRequest } = useAdminAuth();
  const [busy, setBusy] = useState(false);

  const change = async (status) => {
    setBusy(true);
    try {
      await authRequest('/api/users/status/update', {
        method: 'PUT',
        body: { userId: user.userId, accountStatus: status }
      });
      onChanged(status);
      onMessage(`${name || user.mobileNumber} is now ${accountStatusLabel(status).toLowerCase()}.`);
    } catch (err) {
      onMessage(err.message, 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <RowActions
      label={name || user.mobileNumber}
      variant="plain"
      disabled={busy}
      actions={(STATUS_ACTIONS[user.accountStatus] ?? STATUS_ACTIONS.BLOCKED).map((action) => ({
        label: action.label,
        danger: action.danger,
        onClick: () => change(action.status)
      }))}
    />
  );
}
