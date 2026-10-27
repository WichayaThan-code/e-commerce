import { useMemo } from "react";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { isLocalizedEmpty, resolveSocialContent, tiktokEmbedUrl } from "../../../theme/blockContent";
import { BlockHeading, panelClasses, useLocalized } from "./blockParts";
import type { ShopNewBlockProps } from "./types";

/**
 * AG4 SOCIAL: TikTok videos. The merchant only ever supplies numeric video ids (validated `^\d{10,25}$` by the API
 * and again here); the iframe URL is built by this component from a fixed template and never from merchant markup.
 */
export default function SocialSection({ block }: ShopNewBlockProps) {
  const content = useMemo(() => resolveSocialContent(block.content), [block.content]);
  const { tp } = usePublicTranslation();
  const l = useLocalized();

  const videos = content.videoIds
    .map((id) => ({ id, src: tiktokEmbedUrl(id) }))
    .filter((video): video is { id: string; src: string } => video.src != null);
  if (!videos.length && isLocalizedEmpty(content.title)) return null;

  return (
    <section className={panelClasses(block, "shop-social-section")} aria-label={l(content.title) || tp("shop.blocks.social.label")}>
      <BlockHeading title={content.title} prefix="shop-social" />
      {videos.length ? (
        <ul className="shop-social-list">
          {videos.map((video, index) => (
            <li key={`${video.id}-${index}`} className="shop-social-item">
              <iframe
                className="shop-social-frame"
                src={video.src}
                title={tp("shop.blocks.social.videoTitle", { n: index + 1 })}
                loading="lazy"
                sandbox="allow-scripts allow-same-origin allow-popups"
                referrerPolicy="strict-origin-when-cross-origin"
              />
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}
