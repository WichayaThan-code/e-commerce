import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { PackageCheck, Tags, Truck, X } from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
import ReasonDialog from "../admin/ReasonDialog";
import SearchableSelect from "../SearchableSelect";
import { labelOf, uniqueOptions, valueOf } from "./selectOptions";
import { FEATURES } from "../../config/features";
import { usePermissions } from "../../hooks/usePermissions";
import type { Carrier } from "../../services/api";
import type { BulkAction, OrderTag } from "../../services/merchantOrderExtensions";

export type BulkSelectionItem = {
  version: number;
  orderNumber: string;
  status: string;
  awaitingAcceptance: boolean;
};

export type BulkRunPayload =
  | { kind: "accept" }
  | { kind: "process"; status: "READY_TO_PACK" | "PACKING" | "PACKED" }
  | { kind: "ship"; carrierCode: string; trackingNumbers: Record<string, string> }
  | { kind: "cancel"; reason: string }
  | { kind: "tags"; addTagIds: string[]; removeTagIds: string[] };

type Props = {
  selected: Map<string, BulkSelectionItem>;
  carriers: Carrier[];
  tags: OrderTag[];
  progress?: { current: number; total: number } | null;
  running?: boolean;
  onCancelRun?: () => void;
  onRun: (payload: BulkRunPayload) => void;
  onClear: () => void;
};

type Dialog =
  | { kind: "accept" }
  | { kind: "process" }
  | { kind: "ship" }
  | { kind: "cancel" }
  | { kind: "tags" }
  | null;

export default function OrderBulkBar({
  selected,
  carriers,
  tags,
  progress,
  running,
  onCancelRun,
  onRun,
  onClear,
}: Props) {
  const { t } = useTranslation("merchant");
  const { canInShop, deniedTitle } = usePermissions();
  const [dialog, setDialog] = useState<Dialog>(null);
  const [processStatus, setProcessStatus] = useState<"READY_TO_PACK" | "PACKING" | "PACKED">("READY_TO_PACK");
  const [carrierCode, setCarrierCode] = useState("");
  const processOptions = [
    { value: "READY_TO_PACK", label: t("orderManagement.status.READY_TO_PACK") },
    { value: "PACKING", label: t("orderManagement.status.PACKING") },
    { value: "PACKED", label: t("orderManagement.status.PACKED") },
  ];
  const carrierOptions = uniqueOptions([
    { value: "", label: t("orderManagement.filters.all") },
    ...carriers.map((carrier) => ({ value: carrier.code, label: carrier.nameTh || carrier.code })),
  ]);
  const [tracking, setTracking] = useState<Record<string, string>>({});
  const [addTagIds, setAddTagIds] = useState<string[]>([]);
  const count = selected.size;

  const canAccept = canInShop("ORDER_UPDATE");
  const canProcess = canInShop("SHIPMENT_PACK");
  const canShip = canInShop("SHIPMENT_DISPATCH");
  const canCancel = canInShop("ORDER_CANCEL");

  const selectedList = useMemo(
    () => [...selected.entries()].map(([orderId, row]) => ({ orderId, ...row })),
    [selected],
  );

  if (count === 0 && !running) return null;

  const openShip = () => {
    const next: Record<string, string> = {};
    selectedList.forEach((row) => { next[row.orderId] = tracking[row.orderId] ?? ""; });
    setTracking(next);
    setCarrierCode(carriers[0]?.code ?? "");
    setDialog({ kind: "ship" });
  };

  const shipValid = Boolean(carrierCode.trim())
    && selectedList.every((row) => (tracking[row.orderId] ?? "").trim().length > 0);

  return (
    <>
      <div className="pm-bulk-bar om-bulk-bar" role="region" aria-label={t("orderManagement.bulk.barLabel")}>
        <div className="pm-bulk-count">
          <span className="pm-bulk-badge num" aria-hidden="true">{count}</span>
          <b>{t("orderManagement.bulk.selected", { count })}</b>
        </div>
        <div className="pm-bulk-actions">
          {running && progress ? (
            <>
              <span className="om-bulk-progress num">{progress.current}/{progress.total}</span>
              <button type="button" className="ghost" onClick={onCancelRun}>
                <X aria-hidden="true" />
                <span>{t("orderManagement.bulk.stop")}</span>
              </button>
            </>
          ) : (
            <>
              <button type="button" className="primary" disabled={!canAccept} title={deniedTitle(canAccept)} onClick={() => setDialog({ kind: "accept" })}>
                <PackageCheck aria-hidden="true" />
                <span>{t("orderManagement.bulk.accept")}</span>
              </button>
              <button type="button" disabled={!canProcess} title={deniedTitle(canProcess)} onClick={() => setDialog({ kind: "process" })}>
                <span>{t("orderManagement.bulk.process")}</span>
              </button>
              <button type="button" disabled={!canShip} title={deniedTitle(canShip)} onClick={openShip}>
                <Truck aria-hidden="true" />
                <span>{t("orderManagement.bulk.ship")}</span>
              </button>
              <button type="button" disabled={!canCancel} title={deniedTitle(canCancel)} onClick={() => setDialog({ kind: "cancel" })}>
                <span>{t("orderManagement.bulk.cancel")}</span>
              </button>
              {FEATURES.orderTagsApi ? (
                <button type="button" onClick={() => setDialog({ kind: "tags" })}>
                  <Tags aria-hidden="true" />
                  <span>{t("orderManagement.bulk.tags")}</span>
                </button>
              ) : null}
              <button type="button" className="ghost" onClick={onClear}>
                <X aria-hidden="true" />
                <span>{t("orderManagement.bulk.clear")}</span>
              </button>
            </>
          )}
        </div>
      </div>

      {dialog?.kind === "accept" ? (
        <ConfirmDialog
          title={t("orderManagement.bulk.accept")}
          message={t("orderManagement.bulk.confirmAccept", { count })}
          cancelLabel={t("orderManagement.drawer.cancel")}
          confirmLabel={t("orderManagement.bulk.run")}
          onCancel={() => setDialog(null)}
          onConfirm={() => { setDialog(null); onRun({ kind: "accept" }); }}
        />
      ) : null}

      {dialog?.kind === "process" ? (
        <ConfirmDialog
          title={t("orderManagement.bulk.process")}
          message={t("orderManagement.bulk.confirmProcess", { count })}
          cancelLabel={t("orderManagement.drawer.cancel")}
          confirmLabel={t("orderManagement.bulk.run")}
          onCancel={() => setDialog(null)}
          onConfirm={() => { setDialog(null); onRun({ kind: "process", status: processStatus }); }}
        >
          <div className="om-bulk-field">
            <span>{t("orderManagement.bulk.processStatus")}</span>
            <SearchableSelect
              compact
              label={t("orderManagement.bulk.processStatus")}
              options={processOptions.map((opt) => opt.label)}
              value={labelOf(processOptions, processStatus)}
              onChange={(picked) => {
                const next = valueOf(processOptions, picked);
                if (next) setProcessStatus(next as typeof processStatus);
              }}
            />
          </div>
        </ConfirmDialog>
      ) : null}

      {dialog?.kind === "ship" ? (
        <ConfirmDialog
          title={t("orderManagement.bulk.ship")}
          message={t("orderManagement.bulk.confirmShip", { count })}
          cancelLabel={t("orderManagement.drawer.cancel")}
          confirmLabel={t("orderManagement.bulk.run")}
          confirmDisabled={!shipValid}
          onCancel={() => setDialog(null)}
          onConfirm={() => {
            setDialog(null);
            onRun({ kind: "ship", carrierCode: carrierCode.trim(), trackingNumbers: tracking });
          }}
        >
          <div className="om-bulk-field">
            <span>{t("orderManagement.drawer.carrier")}</span>
            <SearchableSelect
              compact
              label={t("orderManagement.drawer.carrier")}
              options={carrierOptions.map((opt) => opt.label)}
              value={labelOf(carrierOptions, carrierCode, carrierOptions[0].label)}
              onChange={(picked) => setCarrierCode(valueOf(carrierOptions, picked))}
            />
          </div>
          <ul className="om-bulk-tracking">
            {selectedList.map((row) => (
              <li key={row.orderId}>
                <span className="num">{row.orderNumber}</span>
                <input
                  className="num"
                  value={tracking[row.orderId] ?? ""}
                  onChange={(event) => setTracking((current) => ({ ...current, [row.orderId]: event.target.value }))}
                  placeholder={t("orderManagement.drawer.tracking")}
                  aria-label={t("orderManagement.bulk.trackingFor", { number: row.orderNumber })}
                />
              </li>
            ))}
          </ul>
        </ConfirmDialog>
      ) : null}

      {dialog?.kind === "cancel" ? (
        <ReasonDialog
          title={t("orderManagement.bulk.cancel")}
          message={t("orderManagement.bulk.confirmCancel", { count })}
          onCancel={() => setDialog(null)}
          onConfirm={(reason) => { setDialog(null); onRun({ kind: "cancel", reason }); }}
        />
      ) : null}

      {dialog?.kind === "tags" ? (
        <ConfirmDialog
          title={t("orderManagement.bulk.tags")}
          message={t("orderManagement.bulk.confirmTags", { count })}
          cancelLabel={t("orderManagement.drawer.cancel")}
          confirmLabel={t("orderManagement.bulk.run")}
          confirmDisabled={addTagIds.length === 0}
          onCancel={() => setDialog(null)}
          onConfirm={() => {
            setDialog(null);
            onRun({ kind: "tags", addTagIds, removeTagIds: [] });
          }}
        >
          <ul className="om-tag-picker-list">
            {tags.map((tag) => (
              <li key={tag.id}>
                <label>
                  <input
                    type="checkbox"
                    checked={addTagIds.includes(tag.id)}
                    onChange={() => setAddTagIds((current) => (
                      current.includes(tag.id)
                        ? current.filter((id) => id !== tag.id)
                        : [...current, tag.id]
                    ))}
                  />
                  {tag.name}
                </label>
              </li>
            ))}
          </ul>
        </ConfirmDialog>
      ) : null}
    </>
  );
}

export type { BulkAction };
