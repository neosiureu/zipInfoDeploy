import { useEffect, useState } from "react";

const KEY = import.meta.env.VITE_VWORLD_KEY;
const DOMAIN = import.meta.env.VITE_VWORLD_DOMAIN ?? "localhost:5173";
const DATA = "LT_C_ADSIGG_INFO";
const BBOX = "box(124.60,33.10,131.87,38.61)";

// export할 배열들
export const CITY = [];
export const TOWN = [];

export default function Gonggong() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [activeTab, setActiveTab] = useState("city");

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
    42: "강원특별자치도",
    43: "충청북도",
    44: "충청남도",
    45: "전북특별자치도",
    46: "전라남도",
    47: "경상북도",
    48: "경상남도",
    50: "제주특별자치도",
  };

  useEffect(() => {
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

        // CITY 배열 생성 (17개 시도)
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

        // TOWN 배열 생성 (252개 시군구)
        const towns = rawData.map((item) => ({
          code: parseInt(item.code.substring(0, 2)),
          fullcode: item.code,
          name: item.gungu,
        }));
        towns.sort((a, b) => {
          if (a.code !== b.code) return a.code - b.code;
          return a.fullcode.localeCompare(b.fullcode);
        });

        // export 배열 업데이트
        CITY.length = 0;
        TOWN.length = 0;
        CITY.push(...cities);
        TOWN.push(...towns);

        setLoading(false);
        console.log(`CITY 배열 생성완료: ${CITY.length}개`, CITY);
        console.log(`TOWN 배열 생성완료: ${TOWN.length}개`, TOWN);
      })
      .catch((e) => {
        setError(String(e));
        setLoading(false);
      });
  }, []);

  if (loading)
    return (
      <div style={{ padding: 50, textAlign: "center", fontSize: 18 }}>
        🔄 API 데이터 로딩중...
      </div>
    );
  if (error)
    return (
      <div style={{ padding: 50, textAlign: "center", color: "red" }}>
        ❌ 에러: {error}
      </div>
    );

  return (
    <div style={{ padding: 30, maxWidth: 1200, margin: "0 auto" }}>
      <h1 style={{ textAlign: "center", marginBottom: 30, color: "#333" }}>
        🌏 전국 시도/시군구 데이터
      </h1>

      {/* 탭 버튼 */}
      <div
        style={{
          display: "flex",
          marginBottom: 20,
          borderBottom: "2px solid #f0f0f0",
        }}
      >
        <button
          onClick={() => setActiveTab("city")}
          style={{
            padding: "15px 30px",
            border: "none",
            backgroundColor: activeTab === "city" ? "#007bff" : "#f8f9fa",
            color: activeTab === "city" ? "white" : "#666",
            cursor: "pointer",
            fontSize: 16,
            fontWeight: "bold",
            borderRadius: "8px 8px 0 0",
            marginRight: 5,
          }}
        >
          📍 CITY 배열 ({CITY.length}개)
        </button>
        <button
          onClick={() => setActiveTab("town")}
          style={{
            padding: "15px 30px",
            border: "none",
            backgroundColor: activeTab === "town" ? "#28a745" : "#f8f9fa",
            color: activeTab === "town" ? "white" : "#666",
            cursor: "pointer",
            fontSize: 16,
            fontWeight: "bold",
            borderRadius: "8px 8px 0 0",
          }}
        >
          🏘️ TOWN 배열 ({TOWN.length}개)
        </button>
      </div>

      {/* CITY 탭 내용 */}
      {activeTab === "city" && (
        <div
          style={{
            backgroundColor: "#f8f9fa",
            padding: 20,
            borderRadius: 10,
            border: "2px solid #007bff",
          }}
        >
          <h2 style={{ color: "#007bff", marginBottom: 20 }}>
            📍 CITY 배열 - 전국 {CITY.length}개 시도
          </h2>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))",
              gap: 15,
            }}
          >
            {CITY.map((city, index) => (
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
                  style={{ fontSize: 18, fontWeight: "bold", color: "#007bff" }}
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

      {/* TOWN 탭 내용 */}
      {activeTab === "town" && (
        <div
          style={{
            backgroundColor: "#f8f9fa",
            padding: 20,
            borderRadius: 10,
            border: "2px solid #28a745",
          }}
        >
          <h2 style={{ color: "#28a745", marginBottom: 20 }}>
            🏘️ TOWN 배열 - 전국 {TOWN.length}개 시군구
          </h2>
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
                  <th style={{ padding: 12, textAlign: "center", width: 80 }}>
                    시도코드
                  </th>
                  <th style={{ padding: 12, textAlign: "center", width: 100 }}>
                    전체코드
                  </th>
                  <th style={{ padding: 12, textAlign: "left" }}>시군구명</th>
                </tr>
              </thead>
              <tbody>
                {TOWN.map((town, index) => (
                  <tr
                    key={town.fullcode}
                    style={{
                      borderBottom: "1px solid #f0f0f0",
                      backgroundColor: index % 2 === 0 ? "#f9f9f9" : "white",
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

      {/* 하단 정보 */}
      <div
        style={{
          marginTop: 30,
          padding: 20,
          backgroundColor: "#e9ecef",
          borderRadius: 10,
          textAlign: "center",
        }}
      >
        <div style={{ fontSize: 16, fontWeight: "bold", marginBottom: 10 }}>
          ✅ 배열 생성 완료!
        </div>
        <div style={{ fontSize: 14, color: "#666" }}>
          다른 컴포넌트에서{" "}
          <code>import {`{ CITY, TOWN }`} from './Gonggong'</code>로 사용 가능
        </div>
      </div>

      {/* 실제 배열 구조 */}
      <div style={{ marginTop: 30 }}>
        <h2 style={{ marginBottom: 20 }}>📋 실제 배열 구조</h2>

        {/* CITY 배열 구조 */}
        <div style={{ marginBottom: 30 }}>
          <h3 style={{ color: "#007bff", marginBottom: 15 }}>
            CITY 배열 구조:
          </h3>
          <pre
            style={{
              backgroundColor: "#f8f9fa",
              padding: 15,
              borderRadius: 5,
              border: "1px solid #dee2e6",
              fontSize: 12,
              overflow: "auto",
              maxHeight: 400,
            }}
          >
            {JSON.stringify(CITY, null, 2)}
          </pre>
        </div>

        {/* TOWN 배열 구조 */}
        <div style={{ marginBottom: 30 }}>
          <h3 style={{ color: "#28a745", marginBottom: 15 }}>
            TOWN 배열 구조:
          </h3>
          <pre
            style={{
              backgroundColor: "#f8f9fa",
              padding: 15,
              borderRadius: 5,
              border: "1px solid #dee2e6",
              fontSize: 12,
              overflow: "auto",
              maxHeight: 400,
            }}
          >
            {JSON.stringify(TOWN, null, 2)}
          </pre>
        </div>

        {/* 배열 요약 정보 */}
        <div
          style={{
            backgroundColor: "#fff3cd",
            padding: 20,
            borderRadius: 10,
            border: "1px solid #ffeaa7",
          }}
        >
          <h4 style={{ marginBottom: 15, color: "#856404" }}>
            📊 배열 정보 요약
          </h4>
          <div style={{ fontSize: 14, lineHeight: 1.6 }}>
            <div>
              <strong>CITY 배열:</strong> {CITY.length}개 항목
            </div>
            <div>
              <strong>TOWN 배열:</strong> {TOWN.length}개 항목
            </div>
            <div>
              <strong>CITY 구조:</strong> {`{ code: 숫자, name: "시도명" }`}
            </div>
            <div>
              <strong>TOWN 구조:</strong>{" "}
              {`{ code: 시도코드, fullcode: "전체코드", name: "시군구명" }`}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
