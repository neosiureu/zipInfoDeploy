import { CITY, TOWN } from "./Gonggong";

export function getLocationName(code) {
  const codeStr = String(code);
  if (codeStr.length === 2) {
    const city = CITY.find((c) => c.code === Number(code));
    return city?.name || "알 수 없음";
  } else if (codeStr.length === 5) {
    const town = TOWN.find((t) => t.fullcode === String(code));
    return town?.name || "알 수 없음";
  }
  return "유효하지 않은 코드";
}
