import { useEffect, useRef, useState } from 'react';
import { Plus } from 'lucide-react';
import "../../css/myPage/menu.css";
import "../../css/myPage/myStock.css";
import Menu from "./Menu";
import { axiosAPI} from '../../api/axiosApi';
import axios from 'axios';

export default function MyPage() {
  const [formData, setFormData] = useState({
    stockType: '0',
    stockSellPrice: '',
    stockFeeMonth: '0',
    stockName: '',
    stockManageFee: '',
    stockInfo: '',
    stockAddress: '',
    stockForm: '0',
    exclusiveArea: '',
    supplyArea: '',
    currentFloor: '',
    floorTotalCount: '',
    roomCount: '',
    bathCount: '',
    stockDirection: '',
    ableDate: '',
    useApprovalDate: '',
    registDate: '',
    stockDetail: '',
    regionNo: '',
  });

  const keyToLabel = {
    stockType: '매물유형이',
    stockSellPrice: '매물 가격이',
    stockFeeMonth: '월세가',
    stockName: '매물 이름이',
    stockInfo: '요약정보가',
    stockAddress: '상세주소가',
    stockForm: '매물 형태가',
    exclusiveArea: '전용 면적이',
    supplyArea: '공급 면적이',
    currentFloor: '현재 층이',
    floorTotalCount: '총 층수가',
    roomCount: '방 개수가',
    bathCount: '욕실 개수가',
    stockDirection: '방향이',
    stockManageFee: '관리비가',
    ableDate: '입주 가능일이',
    useApprovalDate: '사용 승인일이',
    registDate: '최초 등록일이',
    stockDetail: '상세 설명이',
  };  

  const [checkData, setCheckData] = useState({
    stockType: true,
    stockSellPrice: false,
    stockFeeMonth: true,
    stockName: false,
    stockInfo: false,
    stockAddress: false,
    stockForm: true,
    exclusiveArea: false,
    supplyArea: false,
    currentFloor: false,
    floorTotalCount: false,
    roomCount: false,
    bathCount: false,
    stockDirection: false,
    stockManageFee: false,
    ableDate: false,
    useApprovalDate: false,
    registDate: false,
    stockDetail: false,
  });  

  //---------------------- 이미지 관련 구문입니다-------------------------

  const [stockTubmImg, setStockTumbImg] = useState(null);
  const [stockImg, setStockImg] = useState([]);
  const [balanceImg, setBalanceImg] = useState(null);

  // 각각의 input ref
  const thumbInputRef = useRef(null);
  const inputRef = useRef(null);
  const balanceInputRef = useRef(null);
  
  // 각각의 버튼 클릭 핸들러
  const handleThumbClick = () => thumbInputRef.current.click();
  const handleBenefitClick = () => balanceInputRef.current.click();
  const handleClick = () => inputRef.current.click();

  // 파일 선택되면 상태에 추가
  const handleTubmChange = (e) => {
    const file = e.target.files[0];
    setStockTumbImg(file);
  };

  const handleChange = (e) => {
    const files = Array.from(e.target.files);
    setStockImg(prev => [...prev, ...files]);
  };

  const handleBalanceChange = (e) => {
    const file = e.target.files[0];
    setBalanceImg(file);
  };

  const combinedImages = [
  ...(stockTubmImg ? [stockTubmImg] : []),
  ...(balanceImg ? [balanceImg] : []),
  ...stockImg
  ];

  //-----------------------------여기까지-----------------------------

  const [postcode, setPostcode] = useState("");
  const [address, setAddress] = useState("");
  const [detailAddress, setDetailAddress] = useState("");

  const execJusoAPI = (addr) => {
  const callbackName = `jusoCallback_${Date.now()}`;
  window[callbackName] = (data) => {
    if (data.results && data.results.juso && data.results.juso.length > 0) {
      const { admCd } = data.results.juso[0];
      const admCdNo = parseInt(admCd.substring(0, 5));
      setFormData(prev => ({
        ...prev,
        regionNo: admCdNo,
      }));
    } else {
      alert("검색 결과 없음");
    }

    // 정리
    delete window[callbackName];
    document.body.removeChild(script);
  };

  const params = new URLSearchParams({
    currentPage: 1,
    countPerPage: 10,
    resultType: "json",
    confmKey: "devU01TX0FVVEgyMDI1MDYyNDA5NDMxODExNTg3MjU=",
    keyword: addr,
    callback: callbackName,
  });

  const script = document.createElement("script");
  script.src = `https://business.juso.go.kr/addrlink/addrLinkApiJsonp.do?${params.toString()}`;
  document.body.appendChild(script);
};

  const execDaumPostcode = () => {
    new window.daum.Postcode({
      oncomplete: async (data) => {
        const addr =
          data.userSelectedType === "R" ? data.roadAddress : data.jibunAddress;
          setPostcode(data.zonecode);
          setAddress(addr);
        document.getElementsByName("detailAddress")[0].focus();

        execJusoAPI(addr);
      },
    }).open();
    
  };

  
  const handleStockType = (e) => {
    console.log(formData);
    
    const {value} = e.target;
    
    setFormData(prev => ({
      ...prev,
      stockType: value
    }));
  }
  
  const handleStockForm = (e) => {
    const {value} = e.target;
    
    setFormData(prev => ({
      ...prev,
      stockForm: value
    }));
  }
  
  const handleDetailAddress = (e) => {
    const {value} = e.target;
    
    setDetailAddress(value);
    setFormData(prev => ({
      ...prev,
      stockAddress: [postcode, address, detailAddress].join("^^^")
    }))
    formData.stockAddress.length >2 && formData.stockAddress.length < 1000?
      setCheckData((prev) => ({
        ...prev,
        stockAddress: true
      })) :
      setCheckData((prev) => ({
        ...prev,
        stockAddress: false
      })); 
  }
  
  const handleStockInfo = (e) => {
    const { name, value } = e.target;
    
    setFormData((prev) => ({
      ...prev,
      [name]: value
    }));
    
    const trimmed = value.trim();
    
    switch(name) {
      case 'stockSellPrice': !isNaN(trimmed) && trimmed.length > 2 && trimmed.length < 15 ? 
      setCheckData((prev) => ({
        ...prev,
        stockSellPrice: true
      })) :
      setCheckData((prev) => ({
        ...prev,
        stockSellPrice: false
      })); break;
      
      case 'stockFeeMonth': !isNaN(trimmed) && trimmed.length > 2 && trimmed.length < 15 ? 
      setCheckData((prev) => ({
        ...prev,
        stockFeeMonth: true
      })) :
      setCheckData((prev) => ({
        ...prev,
        stockFeeMonth: false
      })); break; 
      
      case 'stockName': trimmed.length > 2 && trimmed.length < 50 ? 
      setCheckData((prev) => ({
        ...prev,
        stockName: true
      })) :
      setCheckData((prev) => ({
        ...prev,
        stockName: false
      })); break;
      
      case 'stockInfo': trimmed.length > 1 && trimmed.length < 100 ? 
      setCheckData((prev) => ({
        ...prev,
        stockInfo: true
      })) :
      setCheckData((prev) => ({
        ...prev,
        stockInfo: false
      })); break;
      
      
      case 'exclusiveArea': !isNaN(trimmed) && trimmed.length > 0 && trimmed.length < 11 ? 
      setCheckData((prev) => ({
        ...prev,
        exclusiveArea: true
      })) :
      setCheckData((prev) => ({
        ...prev,
        exclusiveArea: false
      })); break;
      
      case 'supplyArea': !isNaN(trimmed) && trimmed.length > 0 && trimmed.length < 11 ? 
      setCheckData((prev) => ({
        ...prev,
        supplyArea: true
      })) :
      setCheckData((prev) => ({
        ...prev,
        supplyArea: false
      })); break;
      
      case 'currentFloor': !isNaN(trimmed) && trimmed.length > 0 && trimmed.length < 3 ? 
      setCheckData((prev) => ({
        ...prev,
        currentFloor: true
      })) :
      setCheckData((prev) => ({
        ...prev,
        currentFloor: false
      })); break;
      
      case 'floorTotalCount': !isNaN(trimmed) && trimmed.length > 0 && trimmed.length < 3 ? 
      setCheckData((prev) => ({
        ...prev,
        floorTotalCount: true
      })) :
      setCheckData((prev) => ({
        ...prev,
        floorTotalCount: false
      })); break;
      
      case 'roomCount': !isNaN(trimmed) && trimmed.length > 0 && trimmed.length < 3 ? 
      setCheckData((prev) => ({
        ...prev,
        roomCount: true
      })) :
      setCheckData((prev) => ({
        ...prev,
        roomCount: false
      })); break;
      
      case 'bathCount': !isNaN(trimmed) && trimmed.length > 0 && trimmed.length < 3 ? 
      setCheckData((prev) => ({
        ...prev,
        bathCount: true
      })) :
      setCheckData((prev) => ({
        ...prev,
        bathCount: false
      })); break;
      
      case 'stockDirection': trimmed.length > 0 && trimmed.length < 11 ? 
      setCheckData((prev) => ({
        ...prev,
        stockDirection: true
      })) :
      setCheckData((prev) => ({
        ...prev,
        stockDirection: false
      })); break;
      
      case 'stockManageFee': !isNaN(trimmed) && trimmed.length < 15 ? 
      setCheckData((prev) => ({
        ...prev,
        stockManageFee: true
      })) :
      setCheckData((prev) => ({
        ...prev,
        stockManageFee: false
      })); break;
      
      case 'ableDate': trimmed.length > 0 && trimmed.length < 11 ? 
      setCheckData((prev) => ({
        ...prev,
        ableDate: true
      })) :
      setCheckData((prev) => ({
        ...prev,
        ableDate: false
      })); break;
      
      case 'useApprovalDate': trimmed.length > 0 && trimmed.length < 11 ? 
      setCheckData((prev) => ({
        ...prev,
        useApprovalDate: true
      })) :
      setCheckData((prev) => ({
        ...prev,
        useApprovalDate: false
      })); break;
      
      case 'registDate': trimmed.length > 0 && trimmed.length < 11 ? 
      setCheckData((prev) => ({
        ...prev,
        registDate: true
      })) :
        setCheckData((prev) => ({
          ...prev,
          registDate: false
        })); break;
        
        case 'stockDetail': trimmed.length > 0 && trimmed.length < 2000 ? 
        setCheckData((prev) => ({
          ...prev,
          stockDetail: true
        })) :
        setCheckData((prev) => ({
          ...prev,
          stockDetail: false
        })); break;
      }
    }  
    
const handleSubmit = async () => {
  try {
    
    for (const [key, value] of Object.entries(checkData)) {
      if (!value) {
        const label = keyToLabel[key] || key;
        alert(`${label} 올바르지 않습니다.`);
        return; 
      }
    }

    if (combinedImages.length == 0) {
      alert('이미지 파일을 모두 넣어주세요.');
      return;
    }

    const convertedData = {
      ...formData,
      stockType: parseInt(formData.stockType),
      stockForm: parseInt(formData.stockForm),
      stockSellPrice: parseInt(formData.stockSellPrice),
      stockFeeMonth: parseInt(formData.stockFeeMonth),
      stockManageFee: parseInt(formData.stockManageFee),
      exclusiveArea: parseInt(formData.exclusiveArea),
      supplyArea: parseInt(formData.supplyArea),
      currentFloor: parseInt(formData.currentFloor),
      floorTotalCount: parseInt(formData.floorTotalCount),
      roomCount: parseInt(formData.roomCount),
      bathCount: parseInt(formData.bathCount),
      regionNo: parseInt(formData.regionNo),
    };

      console.log(convertedData);

      const response = await axios.post("http://localhost:8080/myPage/addStock", convertedData,{withCredentials: true});

      if (response.status === 200) {
        console.log("기본 정보 등록 완료");
        const stockNo = response.data.stockNo;
      
        const imageForm = new FormData();
        combinedImages.forEach(file => imageForm.append("stockImg", file));
      
        const imgResp = await axios.post("http://localhost:8080/myPage/addStockImg", imageForm,{withCredentials: true});
      
        if (imgResp.status === 200) {
          alert("매물 등록이 완료되었습니다.");
          nav("/myPage");
        }
      }

    } catch (error) {
        console.log("업로드 실패", error);
    }
  };
    
    
    return (
      
      <div className="my-page">
      <div className="my-page-container">
          
        <Menu/>
        <div className="my-page-stock-sub-tab-container">
          <div className="my-page-stock-sub-tab-nav">
            <button className="my-page-stock-sub-tab-btn">등록한 매물</button>
            <button className="my-page-stock-sub-tab-btn active">매물 등록</button>
            <button className="my-page-stock-sub-tab-btn">최근 본 매물</button>
            <button className="my-page-stock-sub-tab-btn">찜한 매물</button>
          </div>
        </div>

        <div className="my-page-stock-content-card">
          {/* 기본정보 섹션 */}
          <div className="my-page-stock-section">
            <h2 className="my-page-stock-section-title">기본정보</h2>

            <div className="my-page-stock-form-group">

              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">매물유형</label>
                <select onChange={handleStockType} className="my-page-stock-input-field">
                  <option value="0">매매</option>
                  <option value="1">전세</option>
                  <option value="2">월세</option>
                </select>
              </div>

              {formData.stockType === "0" && (
                <>
                <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">매매가</label>
                <input 
                  type="text"
                  placeholder="매매가를 입력해주세요(숫자만 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.stockSellPrice}
                  onChange={handleStockInfo}
                  name='stockSellPrice'
                />
              </div>
              
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">매물명</label>
                <input
                  type="text"
                  placeholder="매물명을 입력해주세요(50글자 이내로 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.stockName}
                  onChange={handleStockInfo}
                  name='stockName'
                />
              </div>

              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">요약정보</label>
                <input 
                  type="text"
                  placeholder="요약정보를 입력해주세요(100글자 이내로 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.stockInfo}
                  onChange={handleStockInfo}
                  name='stockInfo'
                />
              </div>
                </>
              )}
              
              {formData.stockType === "1" && (
                <>
                <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">전세가</label>
                <input 
                  type="text"
                  placeholder="전세가를 입력해주세요(숫자만 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.stockSellPrice}
                  onChange={handleStockInfo}
                  name='stockSellPrice'
                />
              </div>
              
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">매물명</label>
                <input
                  type="text"
                  placeholder="매물명을 입력해주세요(50글자 이내로 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.stockName}
                  onChange={handleStockInfo}
                  name='stockName'
                />
              </div>

              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">요약정보</label>
                <input 
                  type="text"
                  placeholder="요약정보를 입력해주세요(100글자 이내로 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.stockInfo}
                  onChange={handleStockInfo}
                  name='stockInfo'
                />
              </div>
                </>
              )}
              
              {formData.stockType === "2" && (
                <>
                <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">보증금</label>
                <input 
                  type="text"
                  placeholder="보증금을 입력해주세요(숫자만 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.stockSellPrice}
                  onChange={handleStockInfo}
                  name='stockSellPrice'
                />
              </div>

              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">월세가</label>
                <input 
                  type="text"
                  placeholder="월세가를 입력해주세요(숫자만 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.stockFeeMonth}
                  onChange={handleStockInfo}
                  name='stockFeeMonth'
                />
              </div>
              
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">매물명</label>
                <input
                  type="text"
                  placeholder="매물명을 입력해주세요(50글자 이내로 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.stockName}
                  onChange={handleStockInfo}
                  name='stockName'
                />
              </div>

              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">요약정보</label>
                <input 
                  type="text"
                  placeholder="요약정보를 입력해주세요(100글자 이내로 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.stockInfo}
                  onChange={handleStockInfo}
                  name='stockInfo'
                />
              </div>
                </>
              )}


            </div>
          </div>

          {/* 상세정보 섹션 */}
          <div className="my-page-stock-section">
            <h2 className="my-page-stock-section-title">상세정보</h2>
            <div className="my-page-stock-form-group">
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">매물 위치</label>
                <input 
                  type="text"
                  placeholder="우편번호"
                  className="my-page-stock-input-field"
                  value={postcode}
                  name='postcode'
                  readOnly
                />

                <button className='my-page-stock-address-btn' type="button" onClick={execDaumPostcode}>
                  주소 찾기
                </button>
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label"></label>
                <input 
                  type="text"
                  placeholder="주소"
                  className="my-page-stock-input-field"
                  value={address}
                  name='address'
                  readOnly
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label"></label>
                <input 
                  type="text"
                  placeholder="상세 위치를 입력해주세요"
                  className="my-page-stock-input-field"
                  value={detailAddress}
                  name='detailAddress'
                  onChange={handleDetailAddress}
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">매물형태</label>
                <select onChange={handleStockForm} className="my-page-stock-input-field" name='stockForm'>
                  <option value="0">아파트</option>
                  <option value="1">빌라</option>
                  <option value="2">오피스텔</option>
                </select>
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">전용면적</label>
                <input 
                  type="text"
                  placeholder="전용면적을 입력하세요(숫자만 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.exclusiveArea}
                  onChange={handleStockInfo}
                  name='exclusiveArea'
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">공급면적</label>
                <input 
                  type="text"
                  placeholder="공급면적을 입력하세요(숫자만 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.supplyArea}
                  onChange={handleStockInfo}
                  name='supplyArea'
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">해당층</label>
                <input 
                  type="text"
                  placeholder="해당층을 입력하세요(숫자만 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.currentFloor}
                  onChange={handleStockInfo}
                  name='currentFloor'
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">건물층</label>
                <input 
                  type="text"
                  placeholder="건물층을 입력하세요(숫자만 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.floorTotalCount}
                  onChange={handleStockInfo}
                  name='floorTotalCount'
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">방 개수</label>
                <input 
                  type="text"
                  placeholder="방 개수를 입력하세요(숫자만 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.roomCount}
                  onChange={handleStockInfo}
                  name='roomCount'
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">욕실 개수</label>
                <input 
                  type="text"
                  placeholder="욕실 개수를 입력하세요(숫자만 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.bathCount}
                  onChange={handleStockInfo}
                  name='bathCount'
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">방향</label>
                <input 
                  type="text"
                  placeholder="방향을 입력해주세요"
                  className="my-page-stock-input-field"
                  value={formData.stockDirection}
                  onChange={handleStockInfo}
                  name='stockDirection'
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">관리비</label>
                <input 
                  type="text"
                  placeholder="관리비를 입력해주세요(숫자만 입력하세요)"
                  className="my-page-stock-input-field"
                  value={formData.stockManageFee}
                  onChange={handleStockInfo}
                  name='stockManageFee'
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">입주가능일</label>
                <input 
                  type="date"
                  placeholder="입주가능일을 입력해주세요"
                  className="my-page-stock-input-field"
                  value={formData.ableDate}
                  onChange={handleStockInfo}
                  name='ableDate'
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">사용승인일</label>
                <input 
                  type="date"
                  placeholder="사용승인일을 입력해주세요"
                  className="my-page-stock-input-field"
                  value={formData.useApprovalDate}
                  onChange={handleStockInfo}
                  name='useApprovalDate'
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">최초등록일</label>
                <input 
                  type="date"
                  placeholder="최초등록일을 입력해주세요"
                  className="my-page-stock-input-field"
                  value={formData.registDate}
                  onChange={handleStockInfo}
                  name='registDate'
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">상세설명</label>
                <textarea 
                  type="text"
                  placeholder="상세설명을 입력해주세요"
                  className="my-page-stock-textarea"
                  value={formData.stockDetail}
                  onChange={handleStockInfo}
                  name='stockDetail'
                />
              </div>
            </div>
          </div>

          {/* 서체정보 섹션 */}
          <div className="my-page-stock-section">
            <h2 className="my-page-stock-section-title">서체정보</h2>
            <div className="my-page-stock-form-group">
              <div className="my-page-stock-image-upload-section">
                <div className="my-page-stock-image-upload-header">
                  <span className="my-page-stock-image-upload-title">썸네일 이미지</span>
                  <button className="my-page-stock-image-add-btn" onClick={handleThumbClick}>
                    <Plus size={16} className="plus-icon" />
                    이미지추가
                  </button>
                  <input
                    type="file"
                    accept="image/*"
                    multiple
                    ref={thumbInputRef}
                    style={{ display: 'none' }}
                    onChange={handleTubmChange}
                  />
                </div>
                <ul>
                  {stockTubmImg && <li className='imgList'>{stockTubmImg.name}</li>}
                </ul>
                <p className="my-page-stock-image-upload-desc">이미지 파일의 크기는 5MB를 넘으면 안되고 권장되는 크기는 다음과 같습니다.</p>
                <p className="my-page-stock-image-upload-desc">공유할 이미지는 최대로 첨부할 수 있습니다</p>
              </div>
              
              <div className="my-page-stock-image-upload-section">
                <div className="my-page-stock-image-upload-header">
                  <span className="my-page-stock-image-upload-title">평형 이미지</span>
                  <button className="my-page-stock-image-add-btn" onClick={handleBenefitClick}>
                    <Plus size={16} className="plus-icon" />
                    이미지추가
                  </button>
                  <input
                    type="file"
                    accept="image/*"
                    multiple
                    ref={balanceInputRef}
                    style={{ display: 'none' }}
                    onChange={handleBalanceChange}
                  />
                </div>
                <ul>
                  {balanceImg && <li className='imgList'>{balanceImg.name}</li>}
                </ul>
                <p className="my-page-stock-image-upload-desc">이미지 파일의 크기는 5MB를 넘으면 안되고 권장되는 크기는 다음과 같습니다.</p>
                <p className="my-page-stock-image-upload-desc">공유할 이미지는 최대로 첨부할 수 있습니다</p>
              </div>
              
              <div className="my-page-stock-image-upload-section">
                <div className="my-page-stock-image-upload-header">
                  <span className="my-page-stock-image-upload-title">사진</span>
                  <button className="my-page-stock-image-add-btn" onClick={handleClick}>
                    <Plus size={16} className="plus-icon" />
                    이미지추가
                  </button>
                  <input
                    type="file"
                    accept="image/*"
                    multiple
                    ref={inputRef}
                    style={{ display: 'none' }}
                    onChange={handleChange}
                  />
                </div>
                <ul>
                  {stockImg.map((img, idx) => (
                    <li className='imgList' key={idx}>{img.name}</li>
                  ))}
                </ul>
                <p className="my-page-stock-image-upload-desc">이미지 파일의 크기는 5MB를 넘으면 안되고 권장되는 크기는 다음과 같습니다.</p>
                <p className="my-page-stock-image-upload-desc">공유할 이미지는 최대로 첨부할 수 있습니다</p>
              </div>
            </div>
          </div>
          {/* 저장 버튼 */}
          <div className="my-page-stock-save-button-container">
            <button 
              onClick={handleSubmit}
              className="my-page-stock-save-button"
            >
              저장하기
            </button>
          </div>
        </div>
    </div>
  </div>
  );
}