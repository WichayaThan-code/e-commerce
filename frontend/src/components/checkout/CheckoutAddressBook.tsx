import { useEffect, useState } from "react";
import { BookmarkPlus, MapPinned, Trash2 } from "lucide-react";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import ConfirmDialog from "../form/ConfirmDialog";
import {
  listAddresses,
  removeAddress,
  upsertAddress,
  type SavedAddress,
} from "./addressBook";
import type { ThaiAddressValue } from "../ThaiAddressFields";

type Contact = { recipientName: string; phone: string; addressLine1: string; addressLine2: string };

type Props = {
  userId: string;
  contact: Contact;
  area: ThaiAddressValue;
  disabled?: boolean;
  onSelect: (row: SavedAddress) => void;
};

export default function CheckoutAddressBook({ userId, contact, area, disabled, onSelect }: Props) {
  const { tp } = usePublicTranslation();
  const [rows, setRows] = useState<SavedAddress[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [label, setLabel] = useState("");
  const [busy, setBusy] = useState(false);
  const [removeId, setRemoveId] = useState<string | null>(null);
  const [notice, setNotice] = useState("");

  useEffect(() => {
    let cancelled = false;
    void listAddresses(userId).then((next) => {
      if (!cancelled) setRows(next);
    });
    return () => { cancelled = true; };
  }, [userId]);

  const canSave = Boolean(
    contact.recipientName.trim()
    && contact.phone.trim()
    && contact.addressLine1.trim()
    && area.province
    && area.district
    && area.postalCode,
  );

  const saveCurrent = async () => {
    if (!canSave || busy || disabled) return;
    setBusy(true);
    setNotice("");
    try {
      const next = await upsertAddress(userId, {
        label: label.trim() || contact.recipientName.trim(),
        recipientName: contact.recipientName,
        phone: contact.phone,
        addressLine1: contact.addressLine1,
        addressLine2: contact.addressLine2,
        area,
      });
      setRows(next);
      setLabel("");
      setNotice(tp("checkout.addressBook.saved"));
    } finally {
      setBusy(false);
    }
  };

  const pick = (row: SavedAddress) => {
    if (disabled) return;
    setActiveId(row.id);
    onSelect(row);
  };

  const confirmRemove = async () => {
    if (!removeId) return;
    setBusy(true);
    try {
      const next = await removeAddress(userId, removeId);
      setRows(next);
      if (activeId === removeId) setActiveId(null);
      setRemoveId(null);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="checkout-address-book">
      <div className="checkout-address-book-head">
        <MapPinned size={16} aria-hidden="true" />
        <div>
          <b>{tp("checkout.addressBook.title")}</b>
          <small>{tp("checkout.addressBook.hint")}</small>
        </div>
      </div>

      {rows.length > 0 ? (
        <ul className="checkout-address-list">
          {rows.map((row) => (
            <li key={row.id} className={activeId === row.id ? "is-active" : ""}>
              <button type="button" className="checkout-address-pick" disabled={disabled} onClick={() => pick(row)}>
                <span className="checkout-address-label">{row.label}</span>
                <span>{row.recipientName} · <span className="num">{row.phone}</span></span>
                <small>
                  {[row.addressLine1, row.area.subdistrict, row.area.district, row.area.province, row.area.postalCode]
                    .filter(Boolean).join(", ")}
                </small>
              </button>
              <button
                type="button"
                className="checkout-address-remove"
                disabled={disabled || busy}
                aria-label={tp("checkout.addressBook.remove")}
                onClick={() => setRemoveId(row.id)}
              >
                <Trash2 size={14} />
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="checkout-address-empty">{tp("checkout.addressBook.empty")}</p>
      )}

      <div className="checkout-address-save">
        <input
          value={label}
          onChange={(event) => setLabel(event.target.value)}
          placeholder={tp("checkout.addressBook.labelPlaceholder")}
          disabled={disabled || busy}
          maxLength={60}
        />
        <button type="button" disabled={disabled || busy || !canSave} onClick={() => void saveCurrent()}>
          <BookmarkPlus size={14} /> {tp("checkout.addressBook.save")}
        </button>
      </div>
      {notice ? <p className="checkout-address-notice" role="status">{notice}</p> : null}

      {removeId ? (
        <ConfirmDialog
          title={tp("checkout.addressBook.removeTitle")}
          message={tp("checkout.addressBook.removeBody")}
          cancelLabel={tp("cart.dismiss")}
          confirmLabel={tp("checkout.addressBook.remove")}
          danger
          onCancel={() => setRemoveId(null)}
          onConfirm={() => void confirmRemove()}
        />
      ) : null}
    </div>
  );
}
