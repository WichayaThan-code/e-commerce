import {
  Ban,
  Building2,
  Check,
  CheckCircle2,
  CircleX,
  Clock3,
  Download,
  ExternalLink,
  Facebook,
  FileCheck2,
  FileText,
  Globe2,
  History,
  Home,
  Instagram,
  LockKeyhole,
  Mail,
  MapPin,
  MessageCircle,
  Music2,
  Phone,
  Store,
  UsersRound,
  Link2,
} from "lucide-react";
import { mediaUrl } from "../../services/api";
import { maskEmail, maskPhone } from "../../utils/maskContact";
import "../../styles/admin-applications.css";
import AdminDetailDrawer from "./AdminDetailDrawer";

export type ApplicationDetailTab = "info" | "documents" | "history";

export type ApplicationDrawerRecord = {
  id: number;
  logo: string;
  logoClass: string;
  logoUrl: string | null;
  shop: string;
  owner: string;
  phone: string;
  province: string;
  category: string;
  appliedAt: string;
  updatedAt: string;
  status: "APPROVED" | "PENDING_REVIEW" | "SUSPENDED" | "REJECTED";
  email: string;
  address: string;
  description: string;
  website: string;
  lineId: string;
  facebookUrl: string;
  instagramUrl: string;
  tiktokUrl: string;
  documents: { id: string; label: string; filename: string; fileSize: number }[];
  otherShops: string;
};

type Props = {
  item: ApplicationDrawerRecord | null;
  open: boolean;
  tab: ApplicationDetailTab;
  onTabChange: (tab: ApplicationDetailTab) => void;
  onClose: () => void;
  statusLabel: (status: ApplicationDrawerRecord["status"]) => string;
  statusTone: (status: ApplicationDrawerRecord["status"]) => string;
  labels: {
    close: string;
    tabInfo: string;
    tabDocuments: string;
    tabHistory: string;
    appliedAt: (date: string) => string;
    fieldShopId: string;
    fieldShopName: string;
    fieldOwner: string;
    otherShops: (names: string) => string;
    fieldEmail: string;
    fieldPhone: string;
    fieldProvince: string;
    fieldAddress: string;
    fieldCategory: string;
    fieldDescription: string;
    socialTitle: string;
    socialWebsite: string;
    socialEmpty: string;
    socialOpen: string;
    download: string;
    noDocumentsTitle: string;
    historySubmitted: string;
    historyActive: string;
    historyRejected: string;
    historyPending: string;
    historyPendingHint: string;
    historySuspended: string;
    auditLink: string;
    contactOwner: string;
    revealButton: string;
    revealed: string;
    approveShop: string;
    reject: string;
    emptyDetail: string;
  };
  canReview: boolean;
  canRevealContact: boolean;
  canAudit: boolean;
  deniedTitle: (allowed: boolean) => string | undefined;
  busy: boolean;
  revealBusy: boolean;
  revealedContact: { shopId: number; phone: string; email: string } | null;
  onRevealContact: () => void;
  onContactOwner: () => void;
  onApprove: () => void;
  onReject: () => void;
  onDownloadDocument: (documentId: string, filename: string) => void;
  fileSizeLabel: (bytes: number) => string;
  socialLink: (value: string) => { href?: string; label: string };
};

function StatusIcon({ status }: { status: ApplicationDrawerRecord["status"] }) {
  if (status === "APPROVED") return <CheckCircle2 aria-hidden="true" />;
  if (status === "PENDING_REVIEW") return <Clock3 aria-hidden="true" />;
  if (status === "SUSPENDED") return <Ban aria-hidden="true" />;
  return <CircleX aria-hidden="true" />;
}

export default function AdminApplicationDrawer({
  item,
  open,
  tab,
  onTabChange,
  onClose,
  statusLabel,
  statusTone,
  labels,
  canReview,
  canRevealContact,
  canAudit,
  deniedTitle,
  busy,
  revealBusy,
  revealedContact,
  onRevealContact,
  onContactOwner,
  onApprove,
  onReject,
  onDownloadDocument,
  fileSizeLabel,
  socialLink,
}: Props) {
  return (
    <AdminDetailDrawer
      open={open}
      onClose={onClose}
      className="application-drawer"
      closeLabel={labels.close}
      ariaLabel={item?.shop || ""}
      title={
        item ? (
          <span className="application-drawer-title">
            <span className="application-drawer-title__name">{item.shop}</span>
            {item.otherShops ? (
              <span className="owner-siblings" title={labels.otherShops(item.otherShops)}>
                <Link2 aria-hidden="true" />
                <span>{labels.otherShops(item.otherShops)}</span>
              </span>
            ) : null}
          </span>
        ) : ""
      }
      empty={!item ? <p className="om-empty">{labels.emptyDetail}</p> : undefined}
      avatarClassName={item ? `application-drawer-avatar ${item.logoClass}` : undefined}
      avatar={
        item ? (
          item.logoUrl ? <img src={mediaUrl(item.logoUrl)} alt="" /> : <span>{item.logo}</span>
        ) : undefined
      }
      footerClassName="application-drawer-actions"
      meta={
        item ? (
          <>
            <span className={`om-pill app-status-pill ${statusTone(item.status)}`}>
              <StatusIcon status={item.status} />
              {statusLabel(item.status)}
            </span>
            <span className="om-muted">{labels.appliedAt(item.appliedAt)}</span>
          </>
        ) : undefined
      }
      tabs={
        item
          ? [
              { id: "info", label: labels.tabInfo, icon: Store },
              { id: "documents", label: labels.tabDocuments, icon: FileText },
              { id: "history", label: labels.tabHistory, icon: History },
            ]
          : undefined
      }
      activeTab={tab}
      onTabChange={(id) => onTabChange(id as ApplicationDetailTab)}
      footer={
        item ? (
          <>
            {item.status === "PENDING_REVIEW" ? (
              <div className="application-drawer-actions__primary">
                <button
                  type="button"
                  className="danger"
                  disabled={busy || !canReview}
                  title={deniedTitle(canReview)}
                  onClick={onReject}
                >
                  <CircleX aria-hidden="true" />
                  <span>{labels.reject}</span>
                </button>
                <button
                  type="button"
                  className="primary"
                  disabled={busy || !canReview}
                  title={deniedTitle(canReview)}
                  onClick={onApprove}
                >
                  <Check aria-hidden="true" />
                  <span>{labels.approveShop}</span>
                </button>
              </div>
            ) : null}
            <div className="application-drawer-actions__secondary">
              <button type="button" className="quiet" onClick={onContactOwner}>
                <Mail aria-hidden="true" />
                <span>{labels.contactOwner}</span>
              </button>
              {canRevealContact ? (
                <button
                  type="button"
                  className="quiet"
                  disabled={revealBusy || revealedContact?.shopId === item.id}
                  title={deniedTitle(canRevealContact)}
                  onClick={onRevealContact}
                >
                  <Phone aria-hidden="true" />
                  <span>{revealedContact?.shopId === item.id ? labels.revealed : labels.revealButton}</span>
                </button>
              ) : null}
            </div>
          </>
        ) : undefined
      }
    >
      {!item ? null : (
        <>
          {tab === "info" ? (
            <dl className="cm-contact">
              <div className="cm-contact-row">
                <span className="cm-icon-chip" aria-hidden="true"><Home /></span>
                <dt>{labels.fieldShopId}</dt>
                <dd className="num">{item.id}</dd>
              </div>
              <div className="cm-contact-row">
                <span className="cm-icon-chip" aria-hidden="true"><Building2 /></span>
                <dt>{labels.fieldShopName}</dt>
                <dd>{item.shop}</dd>
              </div>
              <div className="cm-contact-row">
                <span className="cm-icon-chip" aria-hidden="true"><UsersRound /></span>
                <dt>{labels.fieldOwner}</dt>
                <dd>{item.owner}</dd>
              </div>
              <div className="cm-contact-row">
                <span className="cm-icon-chip" aria-hidden="true"><Mail /></span>
                <dt>{labels.fieldEmail}</dt>
                <dd>{revealedContact?.shopId === item.id ? revealedContact.email : maskEmail(item.email)}</dd>
              </div>
              <div className="cm-contact-row">
                <span className="cm-icon-chip" aria-hidden="true"><Phone /></span>
                <dt>{labels.fieldPhone}</dt>
                <dd>{revealedContact?.shopId === item.id ? revealedContact.phone : maskPhone(item.phone)}</dd>
              </div>
              <div className="cm-contact-row">
                <span className="cm-icon-chip" aria-hidden="true"><MapPin /></span>
                <dt>{labels.fieldProvince}</dt>
                <dd>{item.province}</dd>
              </div>
              <div className="cm-contact-row">
                <span className="cm-icon-chip" aria-hidden="true"><MapPin /></span>
                <dt>{labels.fieldAddress}</dt>
                <dd>{item.address}</dd>
              </div>
              <div className="cm-contact-row">
                <span className="cm-icon-chip" aria-hidden="true"><FileText /></span>
                <dt>{labels.fieldCategory}</dt>
                <dd>{item.category}</dd>
              </div>
              <div className="cm-contact-row">
                <span className="cm-icon-chip" aria-hidden="true"><LockKeyhole /></span>
                <dt>{labels.fieldDescription}</dt>
                <dd>{item.description}</dd>
              </div>

              <div className="application-social-divider" role="separator">
                <span>{labels.socialTitle}</span>
              </div>

              {(
                [
                  [labels.socialWebsite, item.website, Globe2] as const,
                  ["LINE", item.lineId, MessageCircle] as const,
                  ["Facebook", item.facebookUrl, Facebook] as const,
                  ["Instagram", item.instagramUrl, Instagram] as const,
                  ["TikTok", item.tiktokUrl, Music2] as const,
                ]
              ).map(([label, raw, Icon]) => {
                const link = socialLink(raw || "");
                const filled = Boolean(raw?.trim()) && link.label !== "-";
                return (
                  <div key={label} className="cm-contact-row">
                    <span className="cm-icon-chip" aria-hidden="true"><Icon /></span>
                    <dt>{label}</dt>
                    <dd>
                      {link.href ? (
                        <a href={link.href} target="_blank" rel="noreferrer" title={labels.socialOpen}>
                          <span>{link.label}</span>
                          <ExternalLink aria-hidden="true" />
                        </a>
                      ) : filled ? (
                        link.label
                      ) : (
                        <span className="cm-missing">{labels.socialEmpty}</span>
                      )}
                    </dd>
                  </div>
                );
              })}
            </dl>
          ) : null}

          {tab === "documents" ? (
            <div className="detail-documents">
              {item.documents.length ? (
                item.documents.map((document) => (
                  <p key={document.id}>
                    <FileCheck2 />
                    <span>
                      <b>{document.label}</b>
                      <small>
                        {document.filename} · {fileSizeLabel(document.fileSize)}
                      </small>
                    </span>
                    <button
                      type="button"
                      className="document-download"
                      onClick={() => onDownloadDocument(document.id, document.filename)}
                    >
                      <Download />
                      {labels.download}
                    </button>
                  </p>
                ))
              ) : (
                <p>
                  <FileText />
                  <span>
                    <b>{labels.noDocumentsTitle}</b>
                    <small>-</small>
                  </span>
                </p>
              )}
            </div>
          ) : null}

          {tab === "history" ? (
            <div className="detail-history">
              <p>
                <Clock3 />
                <span>
                  <b>{labels.historySubmitted}</b>
                  <small>{item.appliedAt}</small>
                </span>
              </p>
              {item.status === "APPROVED" ? (
                <p>
                  <FileCheck2 />
                  <span>
                    <b>{labels.historyActive}</b>
                    <small>{item.updatedAt}</small>
                  </span>
                </p>
              ) : null}
              {item.status === "REJECTED" ? (
                <p>
                  <CircleX />
                  <span>
                    <b>{labels.historyRejected}</b>
                    <small>{item.updatedAt}</small>
                  </span>
                </p>
              ) : null}
              {item.status === "PENDING_REVIEW" ? (
                <p>
                  <FileCheck2 />
                  <span>
                    <b>{labels.historyPending}</b>
                    <small>{labels.historyPendingHint}</small>
                  </span>
                </p>
              ) : null}
              {item.status === "SUSPENDED" ? (
                <p>
                  <CircleX />
                  <span>
                    <b>{labels.historySuspended}</b>
                    <small>{item.updatedAt}</small>
                  </span>
                </p>
              ) : null}
              {canAudit ? (
                <p className="ap-muted">
                  <a href={`/admin/audit-logs?targetType=SHOP&targetId=${item.id}&kind=BUSINESS`}>{labels.auditLink}</a>
                </p>
              ) : null}
            </div>
          ) : null}
        </>
      )}
    </AdminDetailDrawer>
  );
}
