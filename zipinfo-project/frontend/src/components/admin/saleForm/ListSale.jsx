import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import "../../../css/admin/saleForm/ListSale.css";

const Advertisement = () => {
  const navigate = useNavigate();
  const [adminName] = useState("홍길동");
  const [adminId] = useState("admin01");
  const [ads, setAds] = useState([
    {
      id: 1,
      name: "썸머힐 아파트",
      type: "아파트",
      author: "관리자1",
      date: "2025-06-16",
    },
    {
      id: 2,
      name: "그린빌 주택",
      type: "주택/빌라",
      author: "관리자2",
      date: "2025-06-15",
    },
    {
      id: 3,
      name: "럭셔리 오피스텔",
      type: "오피스텔",
      author: "관리자1",
      date: "2025-06-14",
    },
  ]);

  const handleDelete = (id) => {
    setAds((prev) => prev.filter((ad) => ad.id !== id));
  };

  const handleUpdate = () => {
    navigate("/admin/add-sale");
  };

  return (
    <div className="p-8 font-sans">
      <h1 className="text-2xl font-bold mb-4">분양 관리</h1>

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
              <th className="p-3">매물번호</th>
              <th className="p-3">매물유형</th>
              <th className="p-3">매물명</th>
              <th className="p-3">작성자</th>
              <th className="p-3">날짜</th>
              <th className="p-3">삭제</th>
            </tr>
          </thead>
          <tbody>
            {ads.map((ad) => (
              <tr key={ad.id} className="border-t">
                <td className="p-3 text-center">{ad.id}</td>
                <td className="p-3 text-center type-name-cell">{ad.type}</td>
                <td className="p-3 text-center sale-name-cell">{ad.name}</td>
                <td className="p-3 text-center">{ad.author}</td>
                <td className="p-3 text-center">{ad.date}</td>
                <td className="p-3 text-center">
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
          onClick={handleUpdate}
        >
          매물 등록
        </button>
      </div>
    </div>
  );
};

export default Advertisement;
