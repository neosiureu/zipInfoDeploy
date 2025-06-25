import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import "../../../css/admin/saleForm/listSale.css";
import { axiosAPI } from "../../../api/axiosAPI";
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
        setSaleList(response.data);
      } catch (error) {
        console.error("분양 매물 목록 불러오기 실패:", error);
      }
    };

    fetchData();
  }, []);

  const handleDelete = (id) => {
    setSaleList((prev) => prev.filter((sale) => sale.saleStockNo !== id));
  };

  const handleAdd = () => {
    navigate("/admin/add_sale");
  };

  const formatDate = (dateString) => {
    if (!dateString) return "";
    return dateString.split("T")[0];
  };

  const saleTypeMap = {
    1: "아파트",
    2: "주택/빌라",
    3: "오피스텔",
  };

  return (
    <div className="ls-container">
      <h1 className="ls-title">분양 관리</h1>

      <div className="ls-admin-box">
        <p>
          현재 <span className="ls-admin-name">{adminName}</span> 으로
          접속중입니다.
        </p>
        <p>
          접속 ID : <span className="ls-admin-id">{adminId}</span>
        </p>
      </div>

      <div className="ls-table-wrapper">
        <table className="ls-table">
          <thead>
            <tr>
              <th>매물번호</th>
              <th>매물유형</th>
              <th>매물명</th>
              <th>작성자</th>
              <th>작성일</th>
              <th>삭제</th>
            </tr>
          </thead>
          <tbody>
            {saleList.length > 0 ? (
              saleList.map((sale) => (
                <tr key={sale.saleStockNo}>
                  <td>
                    <Link to={`/sale/${sale.saleStockNo}`} className="ls-link">
                      {sale.saleStockNo}
                    </Link>
                  </td>
                  <td>{saleTypeMap[sale.saleStockForm] || "기타"}</td>
                  <td>
                    <Link to={`/sale/${sale.saleStockNo}`} className="ls-link">
                      {sale.saleStockName}
                    </Link>
                  </td>
                  <td>{sale.company}</td>
                  <td>{formatDate(sale.announcementDate)}</td>
                  <td>
                    <button
                      className="ls-delete-btn"
                      onClick={() => handleDelete(sale.saleStockNo)}
                    >
                      삭제
                    </button>
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan="6" className="ls-empty">
                  등록된 매물이 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="ls-btn-wrapper">
        <button className="ls-add-btn" onClick={handleAdd}>
          매물 등록
        </button>
      </div>
    </div>
  );
};

export default ListSale;
