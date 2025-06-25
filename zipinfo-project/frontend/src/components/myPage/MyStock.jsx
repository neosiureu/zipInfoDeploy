import { useEffect, useState } from 'react';
import "../../css/myPage/MyStock.css";
import StockMenu from "./StockMenu";
import MiniMenu from "./MiniMenu";
import { axiosAPI } from '../../api/axiosApi';
import { useNavigate } from 'react-router-dom';

export default function MyStock() {
  const [properties, setProperties] = useState([]);

  const nav = useNavigate();

  const fetchProperties = async () => {
  try {
    const response = await axiosAPI.get('/myPage/getMyStock');
    setProperties(response.data);
    console.log(response.data);
  } catch (err) {
    console.error("매물 불러오기 실패:", err);
  }
  };

  useEffect(() => {
    fetchProperties();
  }, []);

  function formatToKoreanCurrency(number) {
  if (!number || isNaN(number)) return '';

  const num = Number(number);
  const 억 = Math.floor(num / 100000000);
  const 만 = Math.floor((num % 100000000) / 10000);

  let result = '';
  if (억 > 0) result += `${억}억`;
  if (만 > 0) result += `${result ? ' ' : ''}${manWithComma(만)}만`;

  return result || '0';
  }

  function manWithComma(num) {
    return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  }

  const getTypeClassName = (type) => {
    switch (type) {
      case '0': return 'property-type-sale';
      case '1': return 'property-type-jeonse';
      case '2': return 'property-type-monthly';
      default: return 'property-type-default';
    }
  };

  const stockTypeLabel = {
    0: '매매',
    1: '전세',
    2: '월세',
  };

  const stockFormLabel = {
    1: '아파트',
    2: '빌라',
    3: '오피스텔',
  };

  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 8;

  const indexOfLastItem = currentPage * itemsPerPage;
  const indexOfFirstItem = indexOfLastItem - itemsPerPage;
  const currentProperties = properties.slice(indexOfFirstItem, indexOfLastItem);
  const totalPages = Math.ceil(properties.length / itemsPerPage);

  const handleDeleteStock = async (stockNo) => {

    const confirmDelete = window.confirm("정말 이 매물을 삭제하시겠습니까?");
    if (!confirmDelete) return;
    try {
      const response = await axiosAPI.post("/myPage/deleteStockInfo", { stockNo: parseInt(stockNo) });
    
      if(response.status === 200){
        alert("삭제되었습니다");
        await fetchProperties();
      }

    } catch (error) {
      console.log(error);
    }
  }

  return (
    <div className="my-page-my-stock">
      <div className="my-page-my-stock-container">
        <StockMenu />
        <MiniMenu />

        <div className="property-container">
          <div className="property-grid">
            {currentProperties.map((property) => {
              const roomInfo = property.stockAddress?.split('^^^')[2] || '';

              return (
                <div key={property.stockNo} className="property-card">
                  {/* 이미지 */}
                  <div className="property-image-container">
                      <div className="property-image-item">
                        <img 
                          src={`http://localhost:8080${property.imgUrls[0]}`} 
                          className="property-image"
                          alt="매물 이미지"
                          loading="lazy"
                        />
                      </div>
                  </div>

                  {/* 본문 */}
                  <div className="property-content">
                    <div className="property-header">
                      <span className="property-category">{stockFormLabel[property.stockForm]} · {property.stockName} {roomInfo}</span>
                    </div>

                    <div className="property-price-container">
                      <span className="property-price">{stockTypeLabel[property.stockType]}</span><span className="property-prices"> {formatToKoreanCurrency(property.stockSellPrice)}</span>
                        {property.stockType === 2 && (
                          <span className="property-fee-month">/{formatToKoreanCurrency(property.stockFeeMonth)}</span>
                        )}
                    </div>

                    <div className="property-details">
                      <div className="property-details-row">
                        <span>{property.currentFloor}/{property.floorTotalCount}층 | {property.supplyArea}㎡ | 관리비 {formatToKoreanCurrency(property.stockManageFee)}원</span>
                      </div>
                    </div>
                  </div>
                  <button className='update-stock-info' onClick={() => nav("/myPage/updateMyStock", { state: property })}>수정</button>
                  <button className='delete-stock-info' onClick={() => handleDeleteStock(property.stockNo)}>삭제</button>
                </div>
              );
            })}
          </div>
          <div className="my-stock-pagination">
            {Array.from({ length: totalPages }, (_, index) => (
              <button
                key={index}
                onClick={() => setCurrentPage(index + 1)}
                className={currentPage === index + 1 ? "active-page" : ""}
              >
                {index + 1}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
