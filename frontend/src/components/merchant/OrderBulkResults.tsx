import { useTranslation } from "react-i18next";
import type { BulkResultItem } from "../../services/merchantOrderExtensions";

export type BulkResultRow = BulkResultItem & { orderNumber: string };

type Props = {
  rows: BulkResultRow[];
  onClose: () => void;
  onSelectFailed: (orderIds: string[]) => void;
};

export default function OrderBulkResults({ rows, onClose, onSelectFailed }: Props) {
  const { t } = useTranslation("merchant");
  const succeeded = rows.filter((row) => row.ok).length;
  const failed = rows.length - succeeded;
  const failedIds = rows.filter((row) => !row.ok).map((row) => row.orderId);

  return (
    <div className="om-bulk-results" role="dialog" aria-modal="true" aria-label={t("orderManagement.bulk.resultsTitle")}>
      <section>
        <header>
          <h2>{t("orderManagement.bulk.resultsTitle")}</h2>
          <p>
            {t("orderManagement.bulk.resultsSummary", {
              ok: succeeded,
              fail: failed,
              total: rows.length,
            })}
          </p>
        </header>
        <div className="om-bulk-results-table">
          <table>
            <thead>
              <tr>
                <th>{t("orderManagement.columns.order")}</th>
                <th>{t("orderManagement.bulk.result")}</th>
                <th>{t("orderManagement.bulk.message")}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.orderId}>
                  <td className="num">{row.orderNumber}</td>
                  <td>{row.ok ? "✓" : "✗"}</td>
                  <td>
                    {row.ok
                      ? t("orderManagement.bulk.ok")
                      : (row.code
                        ? t(`orderManagement.errors.${row.code}`, { defaultValue: row.message || row.code })
                        : (row.message || t("orderManagement.errors.REQUEST_FAILED")))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <footer className="pm-dialog-actions">
          {failedIds.length > 0 ? (
            <button type="button" className="secondary" onClick={() => onSelectFailed(failedIds)}>
              {t("orderManagement.bulk.selectFailed")}
            </button>
          ) : null}
          <button type="button" className="primary" onClick={onClose}>
            {t("orderManagement.drawer.close")}
          </button>
        </footer>
      </section>
    </div>
  );
}
