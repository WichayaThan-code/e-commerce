-- V72: SPX Express had no public tracking URL template, so buyer/merchant UIs could not offer “track on web”.
UPDATE carriers
SET tracking_url_template = 'https://spx.co.th/detail/{tracking}',
    modified_date_time = now()
WHERE code = 'SPX'
  AND (tracking_url_template IS NULL OR tracking_url_template = '');
