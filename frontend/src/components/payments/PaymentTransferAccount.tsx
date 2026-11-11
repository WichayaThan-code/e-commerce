import { Copy, Landmark, QrCode } from "lucide-react";
import type { PaymentAccount } from "../../services/api";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { bankBadge, formatAccountNumber } from "./thaiBanks";

type Props = {
  account: PaymentAccount;
  copyKey: string;
  copiedKey: string | null;
  onCopy: (key: string, value: string) => void;
};

export default function PaymentTransferAccount({ account, copyKey, copiedKey, onCopy }: Props) {
  const { tp } = usePublicTranslation();
  const isPromptPay = account.provider === "PROMPTPAY";
  const badge = bankBadge(account.bankName, isPromptPay);
  const bankLabel = isPromptPay
    ? (account.displayName || tp("checkout.provider.PROMPTPAY"))
    : (account.bankName
      || (account.displayName && account.displayName !== account.accountName ? account.displayName : null)
      || badge.nameTh
      || tp(`checkout.provider.${account.provider}`, { defaultValue: account.provider }));
  const rawValue = (isPromptPay ? account.promptPayId : account.accountNumber) || "";
  const displayValue = isPromptPay ? rawValue : formatAccountNumber(rawValue);
  const numberLabel = isPromptPay
    ? tp("checkout.success.promptPayId")
    : tp("checkout.success.accountNumber");
  const darkText = badge.color.toLowerCase() === "#fec43b";

  return (
    <li className="pay-account-card">
      <div
        className="pay-account-logo"
        style={{ background: badge.color, color: darkText ? "#1a1206" : "#fff" }}
        aria-hidden="true"
      >
        {isPromptPay ? <QrCode size={22} /> : badge.short.length <= 4 ? (
          <span>{badge.short}</span>
        ) : (
          <Landmark size={20} />
        )}
      </div>

      <div className="pay-account-body">
        <p className="pay-account-bank">{bankLabel}</p>

        {rawValue ? (
          <div className="pay-account-row">
            <div className="pay-account-field">
              <span>{numberLabel}</span>
              <b className="num">{displayValue}</b>
            </div>
            <button
              type="button"
              className="pay-account-copy"
              onClick={() => onCopy(copyKey, rawValue)}
            >
              <Copy size={14} />
              {copiedKey === copyKey ? tp("checkout.success.copied") : tp("checkout.success.copyAccount")}
            </button>
          </div>
        ) : null}

        {account.accountName ? (
          <div className="pay-account-field">
            <span>{tp("checkout.success.accountName")}</span>
            <b>{account.accountName}</b>
          </div>
        ) : null}
      </div>
    </li>
  );
}
