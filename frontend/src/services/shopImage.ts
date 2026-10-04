export const SHOP_IMAGE_ACCEPT = "image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp"

export function isShopImage(file: File) {
  const name = file.name.toLowerCase()
  return file.type === "image/jpeg" || file.type === "image/png" || file.type === "image/webp"
    || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")
}
