// 관리자 광고 등록

import React, { useState } from "react";

const Advertisement = () => {
  const [adminName] = useState("홍길동"); // 예시 관리자 이름
  const [adminId] = useState("admin01"); // 예시 관리자 ID
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

  const handleToggleMain = (id) => {
    setAds((prev) =>
      prev.map((ad) => (ad.id === id ? { ...ad, isMain: !ad.isMain } : ad))
    );
  };

  const handleDelete = (id) => {
    setAds((prev) => prev.filter((ad) => ad.id !== id));
  };

  const handleAdUpload = () => {
    alert("광고 등록 페이지로 이동합니다.");
  };

  return (
    <div className="p-8 font-sans">
      <h1 className="text-2xl font-bold mb-4">광고 등록 관리</h1>

      <div className="mb-4 p-4 bg-gray-100 rounded shadow">
        <p>
          현재 <span className="font-semibold">{adminName}</span> 으로
          접속중입니다.
        </p>
        <p>
          접속 ID : <span className="text-blue-600">{adminId}</span>
        </p>
      </div>

      <div className="overflow-x-auto border rounded-lg shadow mb-6">
        <table className="w-full text-sm text-left table-auto">
          <thead className="bg-gray-200">
            <tr>
              <th className="p-3">번호</th>
              <th className="p-3">파일명</th>
              <th className="p-3">작성자</th>
              <th className="p-3">날짜</th>
              <th className="p-3">메인등록</th>
              <th className="p-3">삭제</th>
            </tr>
          </thead>
          <tbody>
            {ads.map((ad, index) => (
              <tr key={ad.id} className="border-t">
                <td className="p-3">{index + 1}</td>
                <td className="p-3">{ad.filename}</td>
                <td className="p-3">{ad.author}</td>
                <td className="p-3">{ad.date}</td>
                <td className="p-3">
                  <button
                    className={`px-3 py-1 text-white rounded ${
                      ad.isMain ? "bg-green-500" : "bg-blue-500"
                    }`}
                    onClick={() => handleToggleMain(ad.id)}
                  >
                    {ad.isMain ? "등록됨" : "등록"}
                  </button>
                </td>
                <td className="p-3">
                  <button
                    className="px-3 py-1 bg-red-500 text-white rounded"
                    onClick={() => handleDelete(ad.id)}
                  >
                    삭제
                  </button>
                </td>
              </tr>
            ))}
            {ads.length === 0 && (
              <tr>
                <td colSpan="6" className="text-center p-4 text-gray-500">
                  등록된 광고가 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="flex justify-end">
        <button
          className="bg-purple-600 hover:bg-purple-700 text-white font-semibold px-5 py-2 rounded"
          onClick={handleAdUpload}
        >
          광고 등록
        </button>
      </div>
    </div>
  );
};

export default Advertisement;
