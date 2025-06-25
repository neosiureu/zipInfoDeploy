import { useEffect, useState } from "react";

const KEY = import.meta.env.VITE_VWORLD_KEY;
const DOMAIN = import.meta.env.VITE_VWORLD_DOMAIN ?? "localhost:5173";
const DATA = "LT_C_ADSIGG_INFO";
const BBOX = "box(124.60,33.10,131.87,38.61)";

//  현재 사용중인 정적 배열들 (하드코딩)
export const CITY = [
  { code: 11, name: "서울특별시" },
  { code: 26, name: "부산광역시" },
  { code: 27, name: "대구광역시" },
  { code: 28, name: "인천광역시" },
  { code: 29, name: "광주광역시" },
  { code: 30, name: "대전광역시" },
  { code: 31, name: "울산광역시" },
  { code: 36, name: "세종특별자치시" },
  { code: 41, name: "경기도" },
  { code: 43, name: "충청북도" },
  { code: 44, name: "충청남도" },
  { code: 46, name: "전라남도" },
  { code: 47, name: "경상북도" },
  { code: 48, name: "경상남도" },
  { code: 50, name: "제주특별자치도" },
  { code: 51, name: "강원특별자치도" },
  { code: 52, name: "전북특별자치도" },
];

export const TOWN = [
  { code: 11, fullcode: "11110", name: "종로구" },
  { code: 11, fullcode: "11140", name: "중구" },
  { code: 11, fullcode: "11170", name: "용산구" },
  { code: 11, fullcode: "11200", name: "성동구" },
  { code: 11, fullcode: "11215", name: "광진구" },
  { code: 11, fullcode: "11230", name: "동대문구" },
  { code: 11, fullcode: "11260", name: "중랑구" },
  { code: 11, fullcode: "11290", name: "성북구" },
  { code: 11, fullcode: "11305", name: "강북구" },
  { code: 11, fullcode: "11320", name: "도봉구" },
  { code: 11, fullcode: "11350", name: "노원구" },
  { code: 11, fullcode: "11380", name: "은평구" },
  { code: 11, fullcode: "11410", name: "서대문구" },
  { code: 11, fullcode: "11440", name: "마포구" },
  { code: 11, fullcode: "11470", name: "양천구" },
  { code: 11, fullcode: "11500", name: "강서구" },
  { code: 11, fullcode: "11530", name: "구로구" },
  { code: 11, fullcode: "11545", name: "금천구" },
  { code: 11, fullcode: "11560", name: "영등포구" },
  { code: 11, fullcode: "11590", name: "동작구" },
  { code: 11, fullcode: "11620", name: "관악구" },
  { code: 11, fullcode: "11650", name: "서초구" },
  { code: 11, fullcode: "11680", name: "강남구" },
  { code: 11, fullcode: "11710", name: "송파구" },
  { code: 11, fullcode: "11740", name: "강동구" },
  { code: 26, fullcode: "26110", name: "중구" },
  { code: 26, fullcode: "26140", name: "서구" },
  { code: 26, fullcode: "26170", name: "동구" },
  { code: 26, fullcode: "26200", name: "영도구" },
  { code: 26, fullcode: "26230", name: "부산진구" },
  { code: 26, fullcode: "26260", name: "동래구" },
  { code: 26, fullcode: "26290", name: "남구" },
  { code: 26, fullcode: "26320", name: "북구" },
  { code: 26, fullcode: "26350", name: "해운대구" },
  { code: 26, fullcode: "26380", name: "사하구" },
  { code: 26, fullcode: "26410", name: "금정구" },
  { code: 26, fullcode: "26440", name: "강서구" },
  { code: 26, fullcode: "26470", name: "연제구" },
  { code: 26, fullcode: "26500", name: "수영구" },
  { code: 26, fullcode: "26530", name: "사상구" },
  { code: 26, fullcode: "26710", name: "기장군" },
  { code: 27, fullcode: "27110", name: "중구" },
  { code: 27, fullcode: "27140", name: "동구" },
  { code: 27, fullcode: "27170", name: "서구" },
  { code: 27, fullcode: "27200", name: "남구" },
  { code: 27, fullcode: "27230", name: "북구" },
  { code: 27, fullcode: "27260", name: "수성구" },
  { code: 27, fullcode: "27290", name: "달서구" },
  { code: 27, fullcode: "27710", name: "달성군" },
  { code: 27, fullcode: "27720", name: "군위군" },
  { code: 28, fullcode: "28110", name: "중구" },
  { code: 28, fullcode: "28140", name: "동구" },
  { code: 28, fullcode: "28177", name: "미추홀구" },
  { code: 28, fullcode: "28185", name: "연수구" },
  { code: 28, fullcode: "28200", name: "남동구" },
  { code: 28, fullcode: "28237", name: "부평구" },
  { code: 28, fullcode: "28245", name: "계양구" },
  { code: 28, fullcode: "28260", name: "서구" },
  { code: 28, fullcode: "28710", name: "강화군" },
  { code: 28, fullcode: "28720", name: "옹진군" },
  { code: 29, fullcode: "29110", name: "동구" },
  { code: 29, fullcode: "29140", name: "서구" },
  { code: 29, fullcode: "29155", name: "남구" },
  { code: 29, fullcode: "29170", name: "북구" },
  { code: 29, fullcode: "29200", name: "광산구" },
  { code: 30, fullcode: "30110", name: "동구" },
  { code: 30, fullcode: "30140", name: "중구" },
  { code: 30, fullcode: "30170", name: "서구" },
  { code: 30, fullcode: "30200", name: "유성구" },
  { code: 30, fullcode: "30230", name: "대덕구" },
  { code: 31, fullcode: "31110", name: "중구" },
  { code: 31, fullcode: "31140", name: "남구" },
  { code: 31, fullcode: "31170", name: "동구" },
  { code: 31, fullcode: "31200", name: "북구" },
  { code: 31, fullcode: "31710", name: "울주군" },
  { code: 36, fullcode: "36110", name: "세종특별자치시" },
  { code: 41, fullcode: "41111", name: "수원시 장안구" },
  { code: 41, fullcode: "41113", name: "수원시 권선구" },
  { code: 41, fullcode: "41115", name: "수원시 팔달구" },
  { code: 41, fullcode: "41117", name: "수원시 영통구" },
  { code: 41, fullcode: "41131", name: "성남시 수정구" },
  { code: 41, fullcode: "41133", name: "성남시 중원구" },
  { code: 41, fullcode: "41135", name: "성남시 분당구" },
  { code: 41, fullcode: "41150", name: "의정부시" },
  { code: 41, fullcode: "41171", name: "안양시 만안구" },
  { code: 41, fullcode: "41173", name: "안양시 동안구" },
  { code: 41, fullcode: "41192", name: "부천시 원미구" },
  { code: 41, fullcode: "41194", name: "부천시 소사구" },
  { code: 41, fullcode: "41196", name: "부천시 오정구" },
  { code: 41, fullcode: "41210", name: "광명시" },
  { code: 41, fullcode: "41220", name: "평택시" },
  { code: 41, fullcode: "41250", name: "동두천시" },
  { code: 41, fullcode: "41271", name: "안산시 상록구" },
  { code: 41, fullcode: "41273", name: "안산시 단원구" },
  { code: 41, fullcode: "41281", name: "고양시 덕양구" },
  { code: 41, fullcode: "41285", name: "고양시 일산동구" },
  { code: 41, fullcode: "41287", name: "고양시 일산서구" },
  { code: 41, fullcode: "41290", name: "과천시" },
  { code: 41, fullcode: "41310", name: "구리시" },
  { code: 41, fullcode: "41360", name: "남양주시" },
  { code: 41, fullcode: "41370", name: "오산시" },
  { code: 41, fullcode: "41390", name: "시흥시" },
  { code: 41, fullcode: "41410", name: "군포시" },
  { code: 41, fullcode: "41430", name: "의왕시" },
  { code: 41, fullcode: "41450", name: "하남시" },
  { code: 41, fullcode: "41461", name: "용인시 처인구" },
  { code: 41, fullcode: "41463", name: "용인시 기흥구" },
  { code: 41, fullcode: "41465", name: "용인시 수지구" },
  { code: 41, fullcode: "41480", name: "파주시" },
  { code: 41, fullcode: "41500", name: "이천시" },
  { code: 41, fullcode: "41550", name: "안성시" },
  { code: 41, fullcode: "41570", name: "김포시" },
  { code: 41, fullcode: "41590", name: "화성시" },
  { code: 41, fullcode: "41610", name: "광주시" },
  { code: 41, fullcode: "41630", name: "양주시" },
  { code: 41, fullcode: "41650", name: "포천시" },
  { code: 41, fullcode: "41670", name: "여주시" },
  { code: 41, fullcode: "41800", name: "연천군" },
  { code: 41, fullcode: "41820", name: "가평군" },
  { code: 41, fullcode: "41830", name: "양평군" },
  { code: 43, fullcode: "43111", name: "청주시 상당구" },
  { code: 43, fullcode: "43112", name: "청주시 서원구" },
  { code: 43, fullcode: "43113", name: "청주시 흥덕구" },
  { code: 43, fullcode: "43114", name: "청주시 청원구" },
  { code: 43, fullcode: "43130", name: "충주시" },
  { code: 43, fullcode: "43150", name: "제천시" },
  { code: 43, fullcode: "43720", name: "보은군" },
  { code: 43, fullcode: "43730", name: "옥천군" },
  { code: 43, fullcode: "43740", name: "영동군" },
  { code: 43, fullcode: "43745", name: "증평군" },
  { code: 43, fullcode: "43750", name: "진천군" },
  { code: 43, fullcode: "43760", name: "괴산군" },
  { code: 43, fullcode: "43770", name: "음성군" },
  { code: 43, fullcode: "43800", name: "단양군" },
  { code: 44, fullcode: "44131", name: "천안시 동남구" },
  { code: 44, fullcode: "44133", name: "천안시 서북구" },
  { code: 44, fullcode: "44150", name: "공주시" },
  { code: 44, fullcode: "44180", name: "보령시" },
  { code: 44, fullcode: "44200", name: "아산시" },
  { code: 44, fullcode: "44210", name: "서산시" },
  { code: 44, fullcode: "44230", name: "논산시" },
  { code: 44, fullcode: "44250", name: "계룡시" },
  { code: 44, fullcode: "44270", name: "당진시" },
  { code: 44, fullcode: "44710", name: "금산군" },
  { code: 44, fullcode: "44760", name: "부여군" },
  { code: 44, fullcode: "44770", name: "서천군" },
  { code: 44, fullcode: "44790", name: "청양군" },
  { code: 44, fullcode: "44800", name: "홍성군" },
  { code: 44, fullcode: "44810", name: "예산군" },
  { code: 44, fullcode: "44825", name: "태안군" },
  { code: 46, fullcode: "46110", name: "목포시" },
  { code: 46, fullcode: "46130", name: "여수시" },
  { code: 46, fullcode: "46150", name: "순천시" },
  { code: 46, fullcode: "46170", name: "나주시" },
  { code: 46, fullcode: "46230", name: "광양시" },
  { code: 46, fullcode: "46710", name: "담양군" },
  { code: 46, fullcode: "46720", name: "곡성군" },
  { code: 46, fullcode: "46730", name: "구례군" },
  { code: 46, fullcode: "46770", name: "고흥군" },
  { code: 46, fullcode: "46780", name: "보성군" },
  { code: 46, fullcode: "46790", name: "화순군" },
  { code: 46, fullcode: "46800", name: "장흥군" },
  { code: 46, fullcode: "46810", name: "강진군" },
  { code: 46, fullcode: "46820", name: "해남군" },
  { code: 46, fullcode: "46830", name: "영암군" },
  { code: 46, fullcode: "46840", name: "무안군" },
  { code: 46, fullcode: "46860", name: "함평군" },
  { code: 46, fullcode: "46870", name: "영광군" },
  { code: 46, fullcode: "46880", name: "장성군" },
  { code: 46, fullcode: "46890", name: "완도군" },
  { code: 46, fullcode: "46900", name: "진도군" },
  { code: 46, fullcode: "46910", name: "신안군" },
  { code: 47, fullcode: "47111", name: "포항시 남구" },
  { code: 47, fullcode: "47113", name: "포항시 북구" },
  { code: 47, fullcode: "47130", name: "경주시" },
  { code: 47, fullcode: "47150", name: "김천시" },
  { code: 47, fullcode: "47170", name: "안동시" },
  { code: 47, fullcode: "47190", name: "구미시" },
  { code: 47, fullcode: "47210", name: "영주시" },
  { code: 47, fullcode: "47230", name: "영천시" },
  { code: 47, fullcode: "47250", name: "상주시" },
  { code: 47, fullcode: "47280", name: "문경시" },
  { code: 47, fullcode: "47290", name: "경산시" },
  { code: 47, fullcode: "47730", name: "의성군" },
  { code: 47, fullcode: "47750", name: "청송군" },
  { code: 47, fullcode: "47760", name: "영양군" },
  { code: 47, fullcode: "47770", name: "영덕군" },
  { code: 47, fullcode: "47820", name: "청도군" },
  { code: 47, fullcode: "47830", name: "고령군" },
  { code: 47, fullcode: "47840", name: "성주군" },
  { code: 47, fullcode: "47850", name: "칠곡군" },
  { code: 47, fullcode: "47900", name: "예천군" },
  { code: 47, fullcode: "47920", name: "봉화군" },
  { code: 47, fullcode: "47930", name: "울진군" },
  { code: 47, fullcode: "47940", name: "울릉군" },
  { code: 48, fullcode: "48121", name: "창원시 의창구" },
  { code: 48, fullcode: "48123", name: "창원시 성산구" },
  { code: 48, fullcode: "48125", name: "창원시 마산합포구" },
  { code: 48, fullcode: "48127", name: "창원시 마산회원구" },
  { code: 48, fullcode: "48129", name: "창원시 진해구" },
  { code: 48, fullcode: "48170", name: "진주시" },
  { code: 48, fullcode: "48220", name: "통영시" },
  { code: 48, fullcode: "48240", name: "사천시" },
  { code: 48, fullcode: "48250", name: "김해시" },
  { code: 48, fullcode: "48270", name: "밀양시" },
  { code: 48, fullcode: "48310", name: "거제시" },
  { code: 48, fullcode: "48330", name: "양산시" },
  { code: 48, fullcode: "48720", name: "의령군" },
  { code: 48, fullcode: "48730", name: "함안군" },
  { code: 48, fullcode: "48740", name: "창녕군" },
  { code: 48, fullcode: "48820", name: "고성군" },
  { code: 48, fullcode: "48840", name: "남해군" },
  { code: 48, fullcode: "48850", name: "하동군" },
  { code: 48, fullcode: "48860", name: "산청군" },
  { code: 48, fullcode: "48870", name: "함양군" },
  { code: 48, fullcode: "48880", name: "거창군" },
  { code: 48, fullcode: "48890", name: "합천군" },
  { code: 50, fullcode: "50110", name: "제주시" },
  { code: 50, fullcode: "50130", name: "서귀포시" },
  { code: 51, fullcode: "51110", name: "춘천시" },
  { code: 51, fullcode: "51130", name: "원주시" },
  { code: 51, fullcode: "51150", name: "강릉시" },
  { code: 51, fullcode: "51170", name: "동해시" },
  { code: 51, fullcode: "51190", name: "태백시" },
  { code: 51, fullcode: "51210", name: "속초시" },
  { code: 51, fullcode: "51230", name: "삼척시" },
  { code: 51, fullcode: "51720", name: "홍천군" },
  { code: 51, fullcode: "51730", name: "횡성군" },
  { code: 51, fullcode: "51750", name: "영월군" },
  { code: 51, fullcode: "51760", name: "평창군" },
  { code: 51, fullcode: "51770", name: "정선군" },
  { code: 51, fullcode: "51780", name: "철원군" },
  { code: 51, fullcode: "51790", name: "화천군" },
  { code: 51, fullcode: "51800", name: "양구군" },
  { code: 51, fullcode: "51810", name: "인제군" },
  { code: 51, fullcode: "51820", name: "고성군" },
  { code: 51, fullcode: "51830", name: "양양군" },
  { code: 52, fullcode: "52111", name: "전주시 완산구" },
  { code: 52, fullcode: "52113", name: "전주시 덕진구" },
  { code: 52, fullcode: "52130", name: "군산시" },
  { code: 52, fullcode: "52140", name: "익산시" },
  { code: 52, fullcode: "52180", name: "정읍시" },
  { code: 52, fullcode: "52190", name: "남원시" },
  { code: 52, fullcode: "52210", name: "김제시" },
  { code: 52, fullcode: "52710", name: "완주군" },
  { code: 52, fullcode: "52720", name: "진안군" },
  { code: 52, fullcode: "52730", name: "무주군" },
  { code: 52, fullcode: "52740", name: "장수군" },
  { code: 52, fullcode: "52750", name: "임실군" },
  { code: 52, fullcode: "52770", name: "순창군" },
  { code: 52, fullcode: "52790", name: "고창군" },
  { code: 52, fullcode: "52800", name: "부안군" },
];

export default function Gonggong() {
  const [activeTab, setActiveTab] = useState("current");
  const [activeSubTab, setActiveSubTab] = useState("city");

  //  API 호출 관련 상태
  const [apiLoading, setApiLoading] = useState(false);
  const [apiError, setApiError] = useState("");
  const [apiCityArray, setApiCityArray] = useState([]);
  const [apiTownArray, setApiTownArray] = useState([]);
  const [newCityCode, setNewCityCode] = useState("");
  const [newTownCode, setNewTownCode] = useState("");

  const provinceCodeToName = {
    11: "서울특별시",
    26: "부산광역시",
    27: "대구광역시",
    28: "인천광역시",
    29: "광주광역시",
    30: "대전광역시",
    31: "울산광역시",
    36: "세종특별자치시",
    41: "경기도",
    43: "충청북도",
    44: "충청남도",
    46: "전라남도",
    47: "경상북도",
    48: "경상남도",
    50: "제주특별자치도",
    51: "강원특별자치도",
    52: "전북특별자치도",
  };

  // 메인 탭 변경 시 서브탭 초기화
  useEffect(() => {
    if (activeTab === "current") {
      setActiveSubTab("city");
    }
  }, [activeTab]);

  //  API에서 새로운 데이터 가져오기
  const fetchNewData = () => {
    setApiLoading(true);
    setApiError("");

    const url = `/vworld/req/data?service=data&request=GetFeature&data=${DATA}&format=json&page=1&size=1000&geomFilter=${encodeURIComponent(
      BBOX
    )}&crs=EPSG:4326&key=${KEY}&domain=${DOMAIN}`;

    fetch(url)
      .then((r) => (r.ok ? r.json() : Promise.reject(`HTTP ${r.status}`)))
      .then((j) => {
        const feats = j?.response?.result?.featureCollection?.features ?? [];
        const rawData = feats.map((f) => ({
          code: f.properties.sig_cd,
          fullname: f.properties.full_nm,
          gungu: f.properties.sig_kor_nm,
        }));

        console.log(` V-World에서 ${rawData.length}개 시군구 데이터 로드 완료`);

        // CITY 배열 생성
        const citySet = new Set();
        const cities = [];

        rawData.forEach((item) => {
          const provinceCode = parseInt(item.code.substring(0, 2));
          if (!citySet.has(provinceCode)) {
            citySet.add(provinceCode);
            cities.push({
              code: provinceCode,
              name: provinceCodeToName[provinceCode] || `코드${provinceCode}`,
            });
          }
        });
        cities.sort((a, b) => a.code - b.code);

        // TOWN 배열 생성
        const towns = rawData.map((item) => ({
          code: parseInt(item.code.substring(0, 2)),
          fullcode: item.code,
          name: item.gungu,
        }));
        towns.sort((a, b) => {
          if (a.code !== b.code) return a.code - b.code;
          return a.fullcode.localeCompare(b.fullcode);
        });

        setApiCityArray(cities);
        setApiTownArray(towns);

        // 새로운 코드 생성
        generateNewCityCode(cities);
        generateNewTownCode(towns);

        setApiLoading(false);
        console.log(
          ` 새 데이터 생성 완료: CITY ${cities.length}개, TOWN ${towns.length}개`
        );
      })
      .catch((e) => {
        setApiError(String(e));
        setApiLoading(false);
      });
  };

  const generateNewCityCode = (cities) => {
    const code = `export const CITY = [
${cities
  .map((city) => `  { code: ${city.code}, name: "${city.name}" },`)
  .join("\n")}
];`;
    setNewCityCode(code);
  };

  const generateNewTownCode = (towns) => {
    const code = `export const TOWN = [
${towns
  .map(
    (town) =>
      `  { code: ${town.code}, fullcode: "${town.fullcode}", name: "${town.name}" },`
  )
  .join("\n")}
];`;
    setNewTownCode(code);
  };

  const copyToClipboard = (text, type) => {
    navigator.clipboard.writeText(text).then(() => {
      alert(`${type} 배열 코드가 클립보드에 복사되었습니다!`);
    });
  };

  return (
    <div style={{ padding: 30, maxWidth: 1400, margin: "0 auto" }}>
      <h1 style={{ textAlign: "center", marginBottom: 30, color: "#333" }}>
        전국 시도/시군구 데이터 관리
      </h1>

      {/* 메인 탭 */}
      <div
        style={{
          display: "flex",
          marginBottom: 30,
          borderBottom: "3px solid #f0f0f0",
        }}
      >
        <button
          onClick={() => setActiveTab("current")}
          style={{
            padding: "15px 30px",
            border: "none",
            backgroundColor: activeTab === "current" ? "#007bff" : "#f8f9fa",
            color: activeTab === "current" ? "white" : "#666",
            cursor: "pointer",
            fontSize: 16,
            fontWeight: "bold",
            borderRadius: "8px 8px 0 0",
            marginRight: 5,
          }}
        >
          현재 사용중 ({CITY.length}개 시도, {TOWN.length}개 시군구)
        </button>
        <button
          onClick={() => setActiveTab("generator")}
          style={{
            padding: "15px 30px",
            border: "none",
            backgroundColor: activeTab === "generator" ? "#28a745" : "#f8f9fa",
            color: activeTab === "generator" ? "white" : "#666",
            cursor: "pointer",
            fontSize: 16,
            fontWeight: "bold",
            borderRadius: "8px 8px 0 0",
          }}
        >
          새 데이터 생성기
        </button>
      </div>

      {/* 현재 데이터 탭 */}
      {activeTab === "current" && (
        <div>
          {/* 현재 데이터 안내 */}
          <div
            style={{
              marginBottom: 20,
              padding: 15,
              backgroundColor: "#d1ecf1",
              border: "1px solid #bee5eb",
              borderRadius: 8,
              textAlign: "center",
              color: "#0c5460",
            }}
          >
            <strong>현재 사용중인 정적 데이터</strong> - API 호출 없이 즉시 사용
            가능합니다.
            <br />
            <small>NeighborhoodBoard에서 이 데이터를 사용하고 있습니다.</small>
          </div>

          {/* 서브 탭 */}
          <div
            style={{
              display: "flex",
              marginBottom: 20,
              borderBottom: "2px solid #f0f0f0",
            }}
          >
            <button
              onClick={() => setActiveSubTab("city")}
              style={{
                padding: "12px 24px",
                border: "none",
                backgroundColor:
                  activeSubTab === "city" ? "#007bff" : "#f8f9fa",
                color: activeSubTab === "city" ? "white" : "#666",
                cursor: "pointer",
                fontSize: 14,
                fontWeight: "bold",
                borderRadius: "5px 5px 0 0",
                marginRight: 5,
              }}
            >
              CITY ({CITY.length}개)
            </button>
            <button
              onClick={() => setActiveSubTab("town")}
              style={{
                padding: "12px 24px",
                border: "none",
                backgroundColor:
                  activeSubTab === "town" ? "#28a745" : "#f8f9fa",
                color: activeSubTab === "town" ? "white" : "#666",
                cursor: "pointer",
                fontSize: 14,
                fontWeight: "bold",
                borderRadius: "5px 5px 0 0",
              }}
            >
              TOWN ({TOWN.length}개)
            </button>
          </div>

          {/* CITY 현재 데이터 */}
          {activeSubTab === "city" && (
            <div
              style={{
                backgroundColor: "#f8f9fa",
                padding: 20,
                borderRadius: 10,
                border: "2px solid #007bff",
              }}
            >
              <h3 style={{ color: "#007bff", marginBottom: 20 }}>
                현재 CITY 배열 - {CITY.length}개 시도
              </h3>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))",
                  gap: 15,
                }}
              >
                {CITY.map((city) => (
                  <div
                    key={city.code}
                    style={{
                      backgroundColor: "white",
                      padding: "15px",
                      borderRadius: 8,
                      border: "1px solid #dee2e6",
                      textAlign: "center",
                    }}
                  >
                    <div
                      style={{
                        fontSize: 18,
                        fontWeight: "bold",
                        color: "#007bff",
                      }}
                    >
                      {city.code}
                    </div>
                    <div style={{ fontSize: 14, color: "#666", marginTop: 5 }}>
                      {city.name}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* TOWN 현재 데이터 */}
          {activeSubTab === "town" && (
            <div
              style={{
                backgroundColor: "#f8f9fa",
                padding: 20,
                borderRadius: 10,
                border: "2px solid #28a745",
              }}
            >
              <h3 style={{ color: "#28a745", marginBottom: 20 }}>
                현재 TOWN 배열 - {TOWN.length}개 시군구
              </h3>
              <div
                style={{
                  maxHeight: 600,
                  overflowY: "auto",
                  backgroundColor: "white",
                  borderRadius: 8,
                  border: "1px solid #dee2e6",
                }}
              >
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead>
                    <tr style={{ backgroundColor: "#28a745", color: "white" }}>
                      <th
                        style={{ padding: 12, textAlign: "center", width: 80 }}
                      >
                        시도코드
                      </th>
                      <th
                        style={{ padding: 12, textAlign: "center", width: 100 }}
                      >
                        전체코드
                      </th>
                      <th style={{ padding: 12, textAlign: "left" }}>
                        시군구명
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {TOWN.map((town, index) => (
                      <tr
                        key={town.fullcode}
                        style={{
                          borderBottom: "1px solid #f0f0f0",
                          backgroundColor:
                            index % 2 === 0 ? "#f9f9f9" : "white",
                        }}
                      >
                        <td
                          style={{
                            padding: 10,
                            textAlign: "center",
                            fontWeight: "bold",
                            color: "#28a745",
                          }}
                        >
                          {town.code}
                        </td>
                        <td
                          style={{
                            padding: 10,
                            textAlign: "center",
                            fontFamily: "monospace",
                          }}
                        >
                          {town.fullcode}
                        </td>
                        <td style={{ padding: 10 }}>{town.name}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* 새 데이터 생성기 탭 */}
      {activeTab === "generator" && (
        <div>
          {/* API 생성기 헤더 */}
          <div
            style={{
              marginBottom: 30,
              padding: 20,
              backgroundColor: "#e8f5e8",
              borderRadius: 10,
              textAlign: "center",
            }}
          >
            <h2 style={{ color: "#2d5a2d", marginBottom: 15 }}>
              V-World API 데이터 생성기
            </h2>
            <div style={{ fontSize: 16, lineHeight: 1.6, marginBottom: 20 }}>
              V-World API에서 실제 252개 시군구 데이터를 가져와서
              <br />
              새로운 CITY, TOWN 배열 코드를 생성합니다.
            </div>

            <button
              onClick={fetchNewData}
              disabled={apiLoading}
              style={{
                padding: "12px 24px",
                backgroundColor: apiLoading ? "#6c757d" : "#28a745",
                color: "white",
                border: "none",
                borderRadius: 8,
                cursor: apiLoading ? "not-allowed" : "pointer",
                fontSize: 16,
                fontWeight: "bold",
              }}
            >
              {apiLoading
                ? " 데이터 로딩 중..."
                : " V-World에서 데이터 가져오기"}
            </button>
          </div>

          {/* API 로딩/에러 상태 */}
          {apiLoading && (
            <div style={{ padding: 20, textAlign: "center", fontSize: 18 }}>
              V-World API에서 시군구 데이터를 가져오는 중...
            </div>
          )}

          {apiError && (
            <div style={{ padding: 20, textAlign: "center", color: "red" }}>
              에러: {apiError}
            </div>
          )}

          {/* API 데이터 로드 완료 시 */}
          {!apiLoading && !apiError && apiCityArray.length > 0 && (
            <div>
              {/* 결과 요약 */}
              <div
                style={{
                  marginBottom: 30,
                  padding: 20,
                  backgroundColor: "#d4edda",
                  borderRadius: 10,
                  textAlign: "center",
                  border: "1px solid #c3e6cb",
                }}
              >
                <h3 style={{ color: "#155724", marginBottom: 15 }}>
                  새 데이터 생성 완료!
                </h3>
                <div style={{ fontSize: 16, lineHeight: 1.6 }}>
                  <div>
                    <strong>새 CITY 배열:</strong> {apiCityArray.length}개 시도
                  </div>
                  <div>
                    <strong>새 TOWN 배열:</strong> {apiTownArray.length}개
                    시군구
                  </div>
                  <div style={{ marginTop: 10, fontSize: 14, color: "#666" }}>
                    아래 코드를 복사해서 위의 export 배열을 교체하세요
                  </div>
                </div>
              </div>

              {/* 새 CITY 배열 코드 */}
              <div style={{ marginBottom: 40 }}>
                <div
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    marginBottom: 15,
                  }}
                >
                  <h3 style={{ color: "#007bff", margin: 0 }}>
                    새 CITY 배열 ({apiCityArray.length}개)
                  </h3>
                  <button
                    onClick={() => copyToClipboard(newCityCode, "새 CITY")}
                    style={{
                      padding: "8px 16px",
                      backgroundColor: "#007bff",
                      color: "white",
                      border: "none",
                      borderRadius: 5,
                      cursor: "pointer",
                      fontSize: 14,
                    }}
                  >
                    복사
                  </button>
                </div>
                <pre
                  style={{
                    backgroundColor: "#f8f9fa",
                    padding: 20,
                    borderRadius: 8,
                    border: "1px solid #dee2e6",
                    fontSize: 13,
                    lineHeight: 1.4,
                    overflow: "auto",
                    maxHeight: 400,
                    fontFamily: "'Monaco', 'Menlo', 'Ubuntu Mono', monospace",
                  }}
                >
                  {newCityCode}
                </pre>
              </div>

              {/* 새 TOWN 배열 코드 */}
              <div style={{ marginBottom: 40 }}>
                <div
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    marginBottom: 15,
                  }}
                >
                  <h3 style={{ color: "#28a745", margin: 0 }}>
                    새 TOWN 배열 ({apiTownArray.length}개)
                  </h3>
                  <button
                    onClick={() => copyToClipboard(newTownCode, "새 TOWN")}
                    style={{
                      padding: "8px 16px",
                      backgroundColor: "#28a745",
                      color: "white",
                      border: "none",
                      borderRadius: 5,
                      cursor: "pointer",
                      fontSize: 14,
                    }}
                  >
                    복사
                  </button>
                </div>
                <pre
                  style={{
                    backgroundColor: "#f8f9fa",
                    padding: 20,
                    borderRadius: 8,
                    border: "1px solid #dee2e6",
                    fontSize: 13,
                    lineHeight: 1.4,
                    overflow: "auto",
                    maxHeight: 600,
                    fontFamily: "'Monaco', 'Menlo', 'Ubuntu Mono', monospace",
                  }}
                >
                  {newTownCode}
                </pre>
              </div>

              {/* 전체 복사 */}
              <div style={{ marginBottom: 40 }}>
                <div
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    marginBottom: 15,
                  }}
                >
                  <h3 style={{ color: "#6f42c1", margin: 0 }}>
                    새 전체 코드 (CITY + TOWN)
                  </h3>
                  <button
                    onClick={() =>
                      copyToClipboard(
                        `${newCityCode}\n\n${newTownCode}`,
                        "새 전체"
                      )
                    }
                    style={{
                      padding: "8px 16px",
                      backgroundColor: "#6f42c1",
                      color: "white",
                      border: "none",
                      borderRadius: 5,
                      cursor: "pointer",
                      fontSize: 14,
                    }}
                  >
                    전체 복사
                  </button>
                </div>

                {/* 사용법 */}
                <div
                  style={{
                    padding: 20,
                    backgroundColor: "#fff3cd",
                    borderRadius: 10,
                    border: "1px solid #ffeaa7",
                  }}
                >
                  <h4 style={{ marginBottom: 15, color: "#856404" }}>사용법</h4>
                  <ol
                    style={{ fontSize: 14, lineHeight: 1.6, color: "#856404" }}
                  >
                    <li>위의 "복사" 버튼을 클릭하여 새 코드를 복사합니다</li>
                    <li>
                      이 파일의 맨 위에 있는 기존 export const CITY, TOWN 배열을
                      대체합니다.
                    </li>
                    <li>복사한 새 코드를 붙여넣습니다</li>
                    <li>
                      파일을 저장하면 NeighborhoodBoard에서 즉시 새 데이터를
                      사용할 수 있습니다
                    </li>
                  </ol>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
