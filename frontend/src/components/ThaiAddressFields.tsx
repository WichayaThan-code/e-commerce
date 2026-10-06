import {
  getDistricts,
  getProvinces,
  getSubDistricts,
  searchAddresses,
} from "@amiearth/thai-address-finder";
import SearchableSelect from "./SearchableSelect";

export type ThaiAddressValue = {
  province: string;
  district: string;
  subdistrict: string;
  postalCode: string;
};

export const emptyThaiAddress: ThaiAddressValue = {
  province: "",
  district: "",
  subdistrict: "",
  postalCode: "",
};

type ThaiAddressFieldsProps = {
  value: ThaiAddressValue;
  onChange: (value: ThaiAddressValue) => void;
  disabled?: boolean;
};

export default function ThaiAddressFields({
  value,
  onChange,
  disabled = false,
}: ThaiAddressFieldsProps) {
  const provinces = getProvinces();
  const districts = value.province ? getDistricts(value.province) : [];
  const subdistricts =
    value.province && value.district
      ? getSubDistricts(value.district, value.province)
      : [];
  const postalCodeOptions =
    value.province && value.district && value.subdistrict
      ? Array.from(
          new Set(
            searchAddresses({
              province: value.province,
              district: value.district,
              subDistrict: value.subdistrict,
            })
              .map((item) => item.postalCode)
              .filter((code): code is string => Boolean(code))
          )
        )
      : [];

  return (
    <div className="address-grid">
      <SearchableSelect
        label="จังหวัด"
        required
        placeholder="เลือกจังหวัด"
        value={value.province}
        options={provinces}
        disabled={disabled}
        onChange={(province) =>
          onChange({
            province,
            district: "",
            subdistrict: "",
            postalCode: "",
          })
        }
      />
      <SearchableSelect
        label="อำเภอ / เขต"
        required
        disabled={disabled || !value.province}
        placeholder={
          !value.province ? "กรุณาเลือกจังหวัดก่อน" : "เลือกอำเภอ"
        }
        value={value.district}
        options={districts}
        onChange={(district) =>
          onChange({
            ...value,
            district,
            subdistrict: "",
            postalCode: "",
          })
        }
      />
      <SearchableSelect
        label="ตำบล / แขวง"
        required
        disabled={disabled || !value.district}
        placeholder={
          !value.district ? "กรุณาเลือกอำเภอก่อน" : "เลือกตำบล"
        }
        value={value.subdistrict}
        options={subdistricts}
        onChange={(subdistrict) => {
          const results = searchAddresses({
            province: value.province,
            district: value.district,
            subDistrict: subdistrict,
          });
          onChange({
            ...value,
            subdistrict,
            postalCode: results[0]?.postalCode || "",
          });
        }}
      />
      <SearchableSelect
        label="รหัสไปรษณีย์"
        required
        disabled={disabled || !value.subdistrict}
        placeholder={
          !value.subdistrict ? "เลือกตำบลก่อน" : "เลือกรหัสไปรษณีย์"
        }
        value={value.postalCode}
        options={postalCodeOptions}
        onChange={(postalCode) => onChange({ ...value, postalCode })}
      />
    </div>
  );
}
