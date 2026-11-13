-- Product reviews: purchased option label snapshot + up to 5 public review photos.

ALTER TABLE product_reviews
    ADD COLUMN IF NOT EXISTS option_label character varying(255);

COMMENT ON COLUMN product_reviews.option_label IS
    'Snapshot of order_items.option_label_snapshot at review time (variant / option the buyer purchased).';

CREATE TABLE IF NOT EXISTS product_review_images (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    review_id uuid NOT NULL,
    media_id uuid NOT NULL,
    sort_order smallint DEFAULT 0 NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pk_product_review_images PRIMARY KEY (id),
    CONSTRAINT uq_product_review_images_review_media UNIQUE (review_id, media_id),
    CONSTRAINT uq_product_review_images_media UNIQUE (media_id),
    CONSTRAINT ck_product_review_images_sort CHECK ((sort_order >= 0) AND (sort_order < 5)),
    CONSTRAINT fk_product_review_images_review FOREIGN KEY (review_id) REFERENCES product_reviews(id),
    CONSTRAINT fk_product_review_images_media FOREIGN KEY (media_id) REFERENCES media_files(id)
);

CREATE INDEX IF NOT EXISTS ix_product_review_images_review_id ON product_review_images (review_id);

COMMENT ON TABLE product_review_images IS
    '[Reviews] product_review_images — photos attached to a product review (max 5 per review).';

ALTER TABLE media_files DROP CONSTRAINT IF EXISTS ck_media_files_purpose;
ALTER TABLE media_files ADD CONSTRAINT ck_media_files_purpose CHECK (purpose IN (
    'PRODUCT', 'SHOP_LOGO', 'SHOP_COVER', 'DOCUMENT', 'BLOG', 'REPORT', 'IMPORT', 'PAYMENT_SLIP',
    'PACKING_PHOTO', 'DELIVERY_PROOF', 'OTHER', 'REFUND_PROOF',
    'ARTICLE_IMAGE', 'HELP_IMAGE', 'REVIEW_IMAGE'));

COMMENT ON COLUMN media_files.purpose IS
    'PRODUCT|SHOP_LOGO|SHOP_COVER|DOCUMENT|BLOG|REPORT|IMPORT|PAYMENT_SLIP|PACKING_PHOTO|DELIVERY_PROOF|OTHER|REFUND_PROOF|ARTICLE_IMAGE|HELP_IMAGE|REVIEW_IMAGE';
