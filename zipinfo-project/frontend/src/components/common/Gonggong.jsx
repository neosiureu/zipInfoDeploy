import { useEffect, useState } from "react";

const KEY = import.meta.env.VITE_VWORLD_KEY;
const DOMAIN = import.meta.env.VITE_VWORLD_DOMAIN ?? "localhost:5173";
const DATA = "LT_C_ADSIGG_INFO";
const BBOX = "box(124.60,33.10,131.87,38.61)";

export default function Gonggong() {
  const [rows, setRows] = useState([]);
  const [error, setError] = useState("");
  const [sqlQuery, setSqlQuery] = useState("");
  const [loading, setLoading] = useState(true);

  /* 1. V-World 252개 한 번에 모두 가져오기 */
  useEffect(() => {
    const url =
      `/vworld/req/data?service=data&request=GetFeature` +
      `&data=${DATA}&format=json&page=1&size=1000` +
      `&geomFilter=${encodeURIComponent(BBOX)}` +
      `&crs=EPSG:4326&key=${KEY}&domain=${DOMAIN}`;

    fetch(url)
      .then((r) => (r.ok ? r.json() : Promise.reject(`HTTP ${r.status}`)))
      .then((j) => {
        const feats = j?.response?.result?.featureCollection?.features ?? [];
        const townData = feats.map((f) => ({
          code: f.properties.sig_cd,
          fullname: f.properties.full_nm,
          gungu: f.properties.sig_kor_nm,
        }));

        setRows(townData);
        generateInsertQuery(townData); // 데이터 로드 후 바로 쿼리 생성
        setLoading(false);
      })
      .catch((e) => {
        setError(String(e));
        setLoading(false);
      });
  }, []);

  /* 2. INSERT 쿼리 생성 */
  const generateInsertQuery = (data) => {
    // BOARD INSERT 문들 생성
    const boardInsertQueries = data.map((row) => {
      const townName = row.gungu.replace(/'/g, "''"); // 작은따옴표 이스케이프
      return `INSERT INTO BOARD (BOARD_NO, BOARD_TITLE, BOARD_CONTENT, BOARD_WRITE_DATE, MEMBER_NO, TOWN_NO, BOARD_SUBJECT) VALUES (SEQ_BOARD.NEXTVAL, '${townName}은 처음입니다.', '${townName} 맛집 추천해주세요', SYSDATE, 1, ${row.code}, 'Q');`;
    });
    let queries = "";
    queries += boardInsertQueries.join("\n");

    queries += "\n-- 확인용 쿼리\n";
    queries += 'SELECT COUNT(*) AS "입력된 게시글 수" FROM BOARD;\n';
    queries +=
      "SELECT BOARD_NO, BOARD_TITLE, BOARD_CONTENT, TOWN_NO FROM BOARD WHERE ROWNUM <= 10; -- 게시글 10개 확인\n";
    queries += "SELECT * FROM BOARD WHERE BOARD_TITLE LIKE '%서울%';";

    setSqlQuery(queries);
  };

  /* 3. 렌더 */
  if (loading)
    return <p style={{ padding: 20 }}>⌛ V-World에서 시군구 데이터 로딩 중…</p>;
  if (error) return <p style={{ color: "red", padding: 20 }}>오류: {error}</p>;

  return (
    <div style={{ padding: 20 }}>
      <div style={{ marginBottom: 20 }}>
        <h2 style={{ color: "#495057", marginBottom: 10 }}>
          전국 시·군·구 게시글 INSERT 쿼리 ({rows.length}개)
        </h2>
      </div>

      <div
        style={{
          backgroundColor: "#f8f9fa",
          border: "1px solid #dee2e6",
          borderRadius: "5px",
          padding: 0,
          overflow: "hidden",
        }}
      >
        <div
          style={{
            backgroundColor: "#e9ecef",
            padding: "12px 15px",
            borderBottom: "1px solid #dee2e6",
            fontWeight: "bold",
            color: "#495057",
          }}
        >
          BOARD 게시글 INSERT 쿼리 (Oracle)
        </div>
        <pre
          style={{
            backgroundColor: "#ffffff",
            padding: 20,
            margin: 0,
            fontSize: "13px",
            lineHeight: "1.4",
            overflow: "auto",
            maxHeight: "80vh",
            whiteSpace: "pre-wrap",
            fontFamily: "'Monaco', 'Menlo', 'Ubuntu Mono', monospace",
          }}
        >
          {sqlQuery}
        </pre>
      </div>
    </div>
  );
}
