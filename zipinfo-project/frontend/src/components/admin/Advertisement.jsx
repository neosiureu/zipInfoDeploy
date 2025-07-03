import React, { useState, useEffect, useRef } from "react";
import axios from "axios";
import "../../css/admin/Advertisement.css";
import { toast } from "react-toastify";

const Advertisement = () => {
  // 🔒 고정된 관리자 정보
  const [adminName] = useState("관리자");
  const [adminId] = useState("admin01");

  const fileInputRef = useRef(null);

  // 📦 광고 리스트 상태 (서버에서 불러오거나 업로드 시 추가)
  const [ads, setAds] = useState([]);

  // 📁 선택한 업로드 파일 상태
  const [selectedFile, setSelectedFile] = useState(null);

  // 컴포넌트 마운트 시 서버에서 광고 리스트 불러오기
  const fetchAds = async () => {
    try {
      const resp = await axios.get(
        "http://localhost:8080/advertisement/list"
      );
      // 서버에서 받아오는 데이터가 아래 형태라 가정
      // [{ id, imageUrl, author, isMain }, ...]
      setAds(resp.data);
    } catch (error) {
      console.error("광고 리스트 불러오기 실패", error);
    }
  };

  useEffect(() => {

    fetchAds();
  }, []);

  // 📌 파일 선택 시 실행되는 이벤트 핸들러
  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    setSelectedFile(file);
  };

  // ✅ 광고 업로드 핸들러 (서버에 파일 저장 요청)
  const handleAdUpload = async () => {
    const maxFileSize = 10 * 1024 * 1024;

    if (!selectedFile) {
      alert("업로드할 파일을 선택해주세요.");
      setSelectedFile(null);
      return;
    }

    if (selectedFile.size > maxFileSize) {
        toast.error("파일 크기는 10MB 이하만 업로드할 수 있습니다.");
        setSelectedFile(null);
        return;
      }

    try {
      const formData = new FormData();
      formData.append("file", selectedFile);

      // 서버에 이미지 파일 전송, 이미지 경로(String) 응답 받음
      const response = await axios.post(
        "http://localhost:8080/advertisement/register",
        formData,
        {withCredentials: true}
      );

      if(response.status === 200){
        fetchAds();
      }
      setSelectedFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
      alert("이미지가 성공적으로 업로드되었습니다.");
    } catch (error) {
      console.error("이미지 업로드 실패", error);
      alert("이미지 업로드에 실패했습니다.");
    }
  };

  // ✅ 광고 메인 등록/해제 토글
  const handleToggleMain = async (adNo) => {
      const response = await axios.post(
        "http://localhost:8080/advertisement/updateMain",
        {adNo:parseInt(adNo)},
        {withCredentials: true}
      );

      fetchAds();

  };

  // ✅ 광고 삭제 (클라이언트 상태에서만 삭제)
  const handleDelete = async (adNo) => {
      const response = await axios.post(
        "http://localhost:8080/advertisement/delete",
        {adNo:parseInt(adNo)},
        {withCredentials: true}
      );

      fetchAds();
  };

  return (
    <div className="admin-ad-wrap">
      <h1 className="admin-ad-title">광고 등록 관리</h1>

      {/* 👤 관리자 정보 표시 */}
      <div className="admin-ad-info">
        <p>
          현재 <span className="admin-ad-name">{adminName}</span> 으로
          접속중입니다.
        </p>
        <p>
          접속 ID : <span className="admin-ad-id">{adminId}</span>
        </p>
      </div>

      {/* 📋 광고 목록 테이블 */}
      <div className="admin-ad-table-box">
        <table className="admin-ad-table">
          <thead>
            <tr>
              <th>번호</th>
              <th>이미지</th>
              <th>작성자</th>
              <th>메인등록</th>
              <th>삭제</th>
            </tr>
          </thead>
          <tbody>
            {ads.length === 0 && (
              <tr>
                <td colSpan="5" className="admin-ad-empty">
                  등록된 광고가 없습니다.
                </td>
              </tr>
            )}
            {ads.map((ad, index) => (
              <tr key={ad.id}>
                <td>{index + 1}</td>
                <td>
                  <img
                    src={`http://localhost:8080${ad.adImgUrl}`} // ✅ 절대 경로로 변경
                    alt={`광고 이미지 ${index + 1}`}
                    style={{ width: "100px", height: "auto" }}
                  />
                </td>
                <td>{ad.memberNickname}</td>
                <td>
                  <button
                    className={`admin-ad-btn ${
                      ad.adMain ? "admin-ad-green" : "admin-ad-blue"
                    }`}
                    onClick={() => handleToggleMain(ad.adNo)}
                  >
                    {ad.adMain ? "등록됨" : "등록"}
                  </button>
                </td>
                <td>
                  <button
                    className="admin-ad-btn admin-ad-red"
                    onClick={() => handleDelete(ad.adNo)}
                  >
                    삭제
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* 📤 이미지 업로드 영역 */}
      <div className="admin-ad-upload">
        <input ref={fileInputRef} type="file" accept="image/*" onChange={handleFileChange} />
      </div>

      {/* 🔘 업로드 버튼 */}
      <div className="admin-ad-action">
        <button className="admin-ad-add" onClick={handleAdUpload}>
          이미지 업로드
        </button>
      </div>
    </div>
  );
};

export default Advertisement;
