import { useState } from 'react';
import { Plus } from 'lucide-react';
import "../../css/myPage/menu.css";
import "../../css/myPage/myStock.css";
import Menu from "./Menu";

export default function MyPage() {
  const [formData, setFormData] = useState({
    name: '',
    title: '',
    responsibility: '',
    mainPhone: '',
    mobilePhone: '',
    email: '',
    address: '',
    businessName: '',
    businessAddress: '',
    businessRep: '',
    businessLicense: '',
    businessPhone: ''
  });

  const [stockType, setStockType] = useState("1");

  const handleInputChange = (field, value) => {
    setFormData(prev => ({
      ...prev,
      [field]: value
    }));
  };

  const handleSubmit = () => {
    console.log('저장된 데이터:', formData);
    alert('저장되었습니다.');
  };

  const handleStockType = (e) => {
    const {value} = e.target;

    setStockType(value);
  }

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

              {stockType === "0" && (
                <>
                <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">매매가</label>
                <input 
                  type="text"
                  placeholder="매매가를 입력해주세요"
                  className="my-page-stock-input-field"
                  value={formData.name}
                  onChange={(e) => handleInputChange('name', e.target.value)}
                />
              </div>
              
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">매물명</label>
                <input
                  type="text"
                  placeholder="매물명을 입력해주세요"
                  className="my-page-stock-input-field"
                  value={formData.responsibility}
                  onChange={(e) => handleInputChange('responsibility', e.target.value)}
                />
              </div>

              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">요약정보</label>
                <input 
                  type="text"
                  placeholder="요약정보를 입력해주세요"
                  className="my-page-stock-input-field"
                  value={formData.title}
                  onChange={(e) => handleInputChange('title', e.target.value)}
                />
              </div>
                </>
              )}
              
              {stockType === "1" && (
                <>
                <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">전세가</label>
                <input 
                  type="text"
                  placeholder="전세가를 입력해주세요"
                  className="my-page-stock-input-field"
                  value={formData.name}
                  onChange={(e) => handleInputChange('name', e.target.value)}
                />
              </div>
              
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">매물명</label>
                <input
                  type="text"
                  placeholder="매물명을 입력해주세요"
                  className="my-page-stock-input-field"
                  value={formData.responsibility}
                  onChange={(e) => handleInputChange('responsibility', e.target.value)}
                />
              </div>

              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">요약정보</label>
                <input 
                  type="text"
                  placeholder="요약정보를 입력해주세요"
                  className="my-page-stock-input-field"
                  value={formData.title}
                  onChange={(e) => handleInputChange('title', e.target.value)}
                />
              </div>
                </>
              )}
              
              {stockType === "2" && (
                <>
                <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">보증금</label>
                <input 
                  type="text"
                  placeholder="보증금을 입력해주세요"
                  className="my-page-stock-input-field"
                  value={formData.name}
                  onChange={(e) => handleInputChange('name', e.target.value)}
                />
              </div>

              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">월세가</label>
                <input 
                  type="text"
                  placeholder="월세가를 입력해주세요"
                  className="my-page-stock-input-field"
                  value={formData.name}
                  onChange={(e) => handleInputChange('name', e.target.value)}
                />
              </div>
              
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">매물명</label>
                <input
                  type="text"
                  placeholder="매물명을 입력해주세요"
                  className="my-page-stock-input-field"
                  value={formData.responsibility}
                  onChange={(e) => handleInputChange('responsibility', e.target.value)}
                />
              </div>

              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">요약정보</label>
                <input 
                  type="text"
                  placeholder="요약정보를 입력해주세요"
                  className="my-page-stock-input-field"
                  value={formData.title}
                  onChange={(e) => handleInputChange('title', e.target.value)}
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
                <label className="my-page-stock-input-label">매물형태</label>
                <input 
                  type="text"
                  placeholder="매물형태를 입력해주세요"
                  className="my-page-stock-input-field"
                  value={formData.mainPhone}
                  onChange={(e) => handleInputChange('mainPhone', e.target.value)}
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">전용/공급면적</label>
                <input 
                  type="text"
                  placeholder="전용/공급면적을 입력하세요"
                  className="my-page-stock-input-field"
                  value={formData.mobilePhone}
                  onChange={(e) => handleInputChange('mobilePhone', e.target.value)}
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">해당층/건물층</label>
                <input 
                  type="text"
                  placeholder="해당층/건물층을 입력하세요"
                  className="my-page-stock-input-field"
                  value={formData.email}
                  onChange={(e) => handleInputChange('email', e.target.value)}
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">방/욕실 수</label>
                <input 
                  type="text"
                  placeholder="방/욕실 수 를 입력하세요"
                  className="my-page-stock-input-field"
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">방향</label>
                <input 
                  type="text"
                  placeholder="방향을 입력해주세요"
                  className="my-page-stock-input-field"
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">관리비</label>
                <input 
                  type="text"
                  placeholder="관리비를 입력해주세요"
                  className="my-page-stock-input-field"
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">입주가능일</label>
                <input 
                  type="text"
                  placeholder="입주가능일을 입력해주세요"
                  className="my-page-stock-input-field"
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">사용승인</label>
                <input 
                  type="text"
                  placeholder="사용승인일을 입력해주세요"
                  className="my-page-stock-input-field"
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">최초등록일</label>
                <input 
                  type="text"
                  placeholder="최초등록일을 입력해주세요"
                  className="my-page-stock-input-field"
                />
              </div>
              <div className="my-page-stock-input-row">
                <label className="my-page-stock-input-label">상세설명</label>
                <textarea 
                  type="text"
                  placeholder="상세설명을 입력해주세요"
                  className="my-page-stock-textarea"
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
                  <span className="my-page-stock-image-upload-title">올릴 이미지</span>
                  <button className="my-page-stock-image-add-btn">
                    <Plus size={16} className="plus-icon" />
                    이미지추가
                  </button>
                </div>
                <p className="my-page-stock-image-upload-desc">이미지 파일의 크기는 5MB를 넘으면 안되고 권장되는 크기는 다음과 같습니다.</p>
                <p className="my-page-stock-image-upload-desc">공유할 이미지는 최대로 첨부할 수 있습니다</p>
              </div>
              
              <div className="my-page-stock-image-upload-section">
                <div className="my-page-stock-image-upload-header">
                  <span className="my-page-stock-image-upload-title">서면</span>
                  <button className="my-page-stock-image-add-btn">
                    <Plus size={16} className="plus-icon" />
                    이미지추가
                  </button>
                </div>
                <p className="my-page-stock-image-upload-desc">이미지 파일의 크기는 5MB를 넘으면 안되고 권장되는 크기는 다음과 같습니다.</p>
                <p className="my-page-stock-image-upload-desc">공유할 이미지는 최대로 첨부할 수 있습니다</p>
              </div>
              
              <div className="my-page-stock-image-upload-section">
                <div className="my-page-stock-image-upload-header">
                  <span className="my-page-stock-image-upload-title">혜택 이미지</span>
                  <button className="my-page-stock-image-add-btn">
                    <Plus size={16} className="plus-icon" />
                    이미지추가
                  </button>
                </div>
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