import React, { useState } from "react";
import axios from "axios";
import "../../css/admin/Advertisement.css";

const Advertisement = () => {
  const [adminName] = useState("홍길동");
  const [adminId] = useState("admin01");
  const [ads, setAds] = useState([
    {
      id: 1,
      filename: "summer-sale.jpg",
      author: "관리자1",
      date: "2025-06-16",
      isMain: false,
    },
    {
      id: 2,
      filename: "new-product.png",
      author: "관리자2",
      date: "2025-06-15",
      isMain: true,
    },
  ]);

  const [selectedFile, setSelectedFile] = useState(null);

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    setSelectedFile(file);
  };

  const handleToggleMain = (id) => {
    setAds((prev) =>
      prev.map((ad) => (ad.id === id ? { ...ad, isMain: !ad.isMain } : ad))
    );
  };

  const handleDelete = (id) => {
    setAds((prev) => prev.filter((ad) => ad.id !== id));
  };

  const handleAdUpload = async () => {
    if (!selectedFile) {
      alert("업로드할 파일을 선택해주세요.");
      return;
    }

    try {
      const formData = new FormData();
      formData.append("file", selectedFile);
      formData.append("author", adminName);
      formData.append("date", new Date().toISOString().slice(0, 10));

      const response = await axios.post(
        "http://localhost:8080/api/advertisements",
        formData,
        {
          headers: {
            "Content-Type": "multipart/form-data",
          },
        }
      );

      const newAd = response.data;
      setAds((prev) => [...prev, newAd]);
      setSelectedFile(null);
      alert("광고가 성공적으로 등록되었습니다.");
    } catch (error) {
      console.error(error);
      alert("광고 등록에 실패했습니다.");
    }
  };

  return (
    <div className="admin-ad-wrap">
      <h1 className="admin-ad-title">광고 등록 관리</h1>

      <div className="admin-ad-info">
        <p>
          현재 <span className="admin-ad-name">{adminName}</span> 으로
          접속중입니다.
        </p>
        <p>
          접속 ID : <span className="admin-ad-id">{adminId}</span>
        </p>
      </div>

      <div className="admin-ad-table-box">
        <table className="admin-ad-table">
          <thead>
            <tr>
              <th>번호</th>
              <th>파일명</th>
              <th>작성자</th>
              <th>날짜</th>
              <th>메인등록</th>
              <th>삭제</th>
            </tr>
          </thead>
          <tbody>
            {ads.length === 0 && (
              <tr>
                <td colSpan="6" className="admin-ad-empty">
                  등록된 광고가 없습니다.
                </td>
              </tr>
            )}

            {ads.map((ad, index) => (
              <tr key={ad.id}>
                <td>{index + 1}</td>
                <td>{ad.filename}</td>
                <td>{ad.author}</td>
                <td>{ad.date}</td>
                <td>
                  <button
                    className={`admin-ad-btn ${
                      ad.isMain ? "admin-ad-green" : "admin-ad-blue"
                    }`}
                    onClick={() => handleToggleMain(ad.id)}
                  >
                    {ad.isMain ? "등록됨" : "등록"}
                  </button>
                </td>
                <td>
                  <button
                    className="admin-ad-btn admin-ad-red"
                    onClick={() => handleDelete(ad.id)}
                  >
                    삭제
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="admin-ad-upload">
        <input type="file" onChange={handleFileChange} />
      </div>

      <div className="admin-ad-action">
        <button className="admin-ad-add" onClick={handleAdUpload}>
          광고 등록
        </button>
      </div>
    </div>
  );
};

export default Advertisement;
