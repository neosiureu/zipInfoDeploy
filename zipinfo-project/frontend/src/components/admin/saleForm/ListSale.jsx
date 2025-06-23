import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import "../../../css/admin/saleForm/ListSale.css";
import { axiosAPI } from "../../../api/axiosApi";

import { Link } from "react-router-dom";

const ListSale = () => {
  const navigate = useNavigate();
  const [adminName] = useState("홍길동");
  const [adminId] = useState("admin01");

  const [saleList, setSaleList] = useState([]);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await axiosAPI.get("/admin/selectSaleList");
        setSaleList(response.data); // DB에서 불러온 매물 목록
      } catch (error) {
        console.error("분양 매물 목록 불러오기 실패:", error);
      }
    };

    fetchData();
  }, []);

  const handleDelete = (id) => {
    setSaleList((prev) => prev.filter((sale) => sale.saleStockNo !== id));
  };

  const handleUpdate = () => {
    navigate("/admin/add-sale");
  };

  const formatDate = (dateString) => {
    if (!dateString) return "";
    return dateString.split("T")[0]; // yyyy-MM-dd 형식으로 자르기
  };

  const saleTypeMap = {
    1: "아파트",
    2: "주택/빌라",
    3: "오피스텔",
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
              <th className="p-3 text-center">매물번호</th>
              <th className="p-3 text-center">매물유형</th>
              <th className="p-3 text-center">매물명</th>
              <th className="p-3 text-center">작성자</th>
              <th className="p-3 text-center">작성일</th>
              <th className="p-3 text-center">삭제</th>
            </tr>
          </thead>
          <tbody>
            {saleList.length > 0 ? (
              saleList.map((sale) => (
                <tr key={sale.saleStockNo} className="border-t">
                  <td className="p-3 text-center">
                    <Link
                      to={`/sale/${sale.saleStockNo}`}
                      className="text-blue-600 underline hover:text-blue-800"
                    >
                      {sale.saleStockNo}
                    </Link>
                  </td>
                  <td className="p-3 text-center">
                    {saleTypeMap[sale.saleStockForm] || "기타"}
                  </td>
                  <td className="p-3 text-center">
                    <Link
                      to={`/sale/${sale.saleStockNo}`}
                      className="text-blue-600 underline hover:text-blue-800"
                    >
                      {sale.saleStockName}
                    </Link>
                  </td>
                  <td className="p-3 text-center">{sale.company}</td>
                  <td className="p-3 text-center">
                    {formatDate(sale.announcementDate)}
                  </td>
                  <td className="p-3 text-center">
                    <button
                      className="px-3 py-1 bg-red-500 text-white rounded"
                      onClick={() => handleDelete(sale.saleStockNo)}
                    >
                      삭제
                    </button>
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan="6" className="text-center p-4 text-gray-500">
                  등록된 매물이 없습니다.
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

export default ListSale;
