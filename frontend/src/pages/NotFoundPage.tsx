import { Link, useParams } from "react-router-dom";
import { useDocumentMeta } from "../hooks/useDocumentMeta";
import { usePublicTranslation } from "../hooks/usePublicTranslation";

type NotFoundPageProps = {
  kind?: "page" | "shop" | "product";
  bare?: boolean;
};

export default function NotFoundPage({ kind = "page", bare = false }: NotFoundPageProps) {
  const { tp } = usePublicTranslation();
  const { slug } = useParams();
  useDocumentMeta({ title: tp("notFound.title") });

  const body =
    kind === "shop"
      ? tp("notFound.shopBody")
      : kind === "product"
        ? tp("notFound.productBody")
        : tp("notFound.pageBody");

  const content = (
    <section className="recommend-width">
      <article className="shop-list-empty">
        <h1>{tp("notFound.title")}</h1>
        <p>{body}</p>
        <p>
          <Link to="/">{tp("notFound.backHome")}</Link>
          {kind === "product" && slug ? (
            <>
              {" · "}
              <Link to={`/shop/${slug}`}>{tp("notFound.backShop")}</Link>
            </>
          ) : null}
        </p>
      </article>
    </section>
  );

  if (bare) return content;
  return <div className="recommend-page">{content}</div>;
}
