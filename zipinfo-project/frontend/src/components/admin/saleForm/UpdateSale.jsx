import React, { useState, useEffect } from "react";
import axios from "axios";
import "../../../css/admin/saleForm/UpdateSale.css";
import { useNavigate, useParams } from "react-router-dom";

// 매물 형태와 분양 상태를 숫자로 매핑
const typeMap = { 아파트: 1, 빌라: 2, 오피스텔: 3 };
const statusMap = { 분양중: 1, 분양예정: 2, 분양완료: 3 };

// 역매핑 (숫자 -> 텍스트)
const reverseTypeMap = { 1: "아파트", 2: "빌라", 3: "오피스텔" };
const reverseStatusMap = { 1: "분양중", 2: "분양예정", 3: "분양완료" };

// 초기 상태 정의
const initialState = {
  type: "아파트",
  name: "",
  status: "분양예정",
  address: "",
  scale: "",
  startDate: "",
  endDate: "",
  recruitDate: "",
  builder: "",
  contact: "",
  tax: "",
  supplyArea: "",
  exclusiveArea: "",
  rooms: "",
  baths: "",
  price1: "",
  price2: "",
  contractRate: "",
  contractAmount: "",
  interimRate: "",
  interimAmount: "",
  balanceRate: "",
  balanceAmount: "",
  regionNo: "",
  lat: 0,
  lng: 0,
};

const UpdateSale = () => {
  const { id } = useParams();
  const [form, setForm] = useState(initialState); // 폼 입력 상태 관리
  const [submitStatus, setSubmitStatus] = useState(""); // 수정 상태 메시지
  const [availableInterimRates, setAvailableInterimRates] = useState([]); // 중도금 선택 옵션
  const [thumbnailImages, setThumbnailImages] = useState([]); // 썸네일 이미지 파일들
  const [floorImages, setFloorImages] = useState([]); // 평면도 이미지 파일들
  const [loading, setLoading] = useState(true); // 로딩 상태

  const formatWithComma = (val) => val.replace(/\B(?=(\d{3})+(?!\d))/g, ","); // 숫자에 콤마 포맷 적용
  const removeComma = (val) => val.replace(/,/g, ""); // 콤마 제거

  // 기존 이미지 이름을 저장할 상태 추가
  const [existingThumbnailNames, setExistingThumbnailNames] = useState([]);
  const [existingFloorNames, setExistingFloorNames] = useState([]);

  const navigate = useNavigate();

  const totalPrice =
    (Number(removeComma(form.price1)) * 10000 +
      Number(removeComma(form.price2))) *
    10000;

  // 기존 매물 정보 불러오기
  useEffect(() => {
    const fetchSaleData = async () => {
      try {
        setLoading(true);
        const response = await axios.get(
          `http://localhost:8080/admin/updateSale/${id}`,
          {
            withCredentials: true,
          }
        );

        if (response.status === 200) {
          const saleData = response.data;

          // 이미지 원본명 저장
          const images = saleData.imageList || [];
          const thumbnails = images.filter(
            (img) =>
              img.sale_img_url && img.sale_img_url.includes("/thumbnail/")
          );
          const floors = images.filter(
            (img) => img.sale_img_url && img.sale_img_url.includes("/floor/")
          );

          setExistingThumbnailNames(
            thumbnails.map((img) => img.sale_origin_name)
          );
          setExistingFloorNames(floors.map((img) => img.sale_origin_name));

          console.log("🚀 전체 saleData:", saleData);
          console.log("🖼️ 이미지 리스트:", saleData.imageList);
          console.log("imageList", saleData.imageList);

          // 분양가를 억/만원으로 분리
          const totalPrice = saleData.salePrice;
          const price1 = Math.floor(totalPrice / 100000000); // 억 단위
          const price2 = Math.floor((totalPrice % 100000000) / 10000); // 만원 단위

          // 계약금, 중도금 비율 계산
          // const contractRate =
          //   totalPrice > 0
          //     ? Math.round((saleData.deposit / totalPrice) * 100)
          //     : 0;
          // const interimRate =
          //   totalPrice > 0
          //     ? Math.round((saleData.middlePayment / totalPrice) * 100)
          //     : 0;

          // 금액 → 한글 통화 포맷
          const toKoreanCurrency = (amount) => {
            const billion = Math.floor(amount / 100000000);
            const million = Math.floor((amount % 100000000) / 10000);

            if (billion > 0 && million > 0)
              return `${billion}억 ${million.toLocaleString()}만원`;
            if (billion > 0) return `${billion}억`;
            if (million > 0) return `${million.toLocaleString()}만원`;
            return `${amount.toLocaleString()}원`;
          };

          const deposit = saleData.deposit || 0;
          const middlePayment = saleData.middlePayment || 0;
          const balancePayment = saleData.balancePayment || 0;

          const contractRate =
            totalPrice > 0 ? Math.round((deposit / totalPrice) * 100) : 0;
          const interimRate =
            totalPrice > 0 ? Math.round((middlePayment / totalPrice) * 100) : 0;
          const balanceRate = 100 - contractRate - interimRate;

          setForm({
            type: reverseTypeMap[saleData.saleStockForm] || "아파트",
            name: saleData.saleStockName || "",
            status: reverseStatusMap[saleData.saleStatus] || "분양예정",
            address: saleData.saleAddress || "",
            scale: saleData.scale || "",
            startDate: saleData.applicationStartDate
              ? saleData.applicationStartDate.split(/[T\s]/)[0]
              : "",
            endDate: saleData.applicationEndDate
              ? saleData.applicationEndDate.split(/[T\s]/)[0]
              : "",
            recruitDate: saleData.announcementDate
              ? saleData.announcementDate.split(/[T\s]/)[0]
              : "",
            builder: saleData.company || "",
            contact: saleData.contactInfo || "",
            tax: saleData.acquisitionTax
              ? saleData.acquisitionTax.toString()
              : "",
            supplyArea: saleData.saleSupplyArea
              ? saleData.saleSupplyArea.toString()
              : "",
            exclusiveArea: saleData.saleExclusiveArea
              ? saleData.saleExclusiveArea.toString()
              : "",
            rooms: saleData.saleRoomCount
              ? saleData.saleRoomCount.toString()
              : "",
            baths: saleData.saleBathroomCount
              ? saleData.saleBathroomCount.toString()
              : "",
            price1: price1.toString(),
            price2: price2.toString(),
            contractRate: contractRate.toString(),
            contractAmount: toKoreanCurrency(deposit), // 서버에서 받아온 값
            interimRate: interimRate.toString(),
            interimAmount: toKoreanCurrency(middlePayment),
            balanceRate: `${balanceRate}%`,
            balanceAmount: toKoreanCurrency(balancePayment),
            regionNo: saleData.regionNo ? saleData.regionNo.toString() : "",
            lat: saleData.lat || 0,
            lng: saleData.lng || 0,
          });
        }
      } catch (error) {
        console.error("매물 정보 불러오기 실패:", error);
        alert("매물 정보를 불러오는데 실패했습니다.");
        navigate("/admin/list_sale");
      } finally {
        setLoading(false);
      }
    };

    if (id) {
      fetchSaleData();
    }
  }, [id, navigate]);

  // 주소를 통해 위도/경도 좌표 조회 (Kakao API)
  const getCoordsByAddress = async (address) => {
    const response = await axios.get(
      `https://dapi.kakao.com/v2/local/search/address.json?query=${encodeURIComponent(
        address
      )}`,
      {
        headers: {
          Authorization: `KakaoAK ${import.meta.env.VITE_KAKAO_REST_API_KEY}`,
        },
      }
    );
    const result = response.data.documents[0];
    if (!result) throw new Error("주소에 해당하는 위치가 없습니다.");
    return { lat: result.y, lng: result.x };
  };

  // 계약금 비율에 따라 중도금 선택 가능 범위 조정
  useEffect(() => {
    const contract = parseInt(form.contractRate) || 0;
    const options = [];
    for (let i = 10; i <= 100 - contract; i += 10) {
      options.push(i);
    }
    setAvailableInterimRates(contract ? options : []);

    const currentInterim = parseInt(form.interimRate) || 0;
    if (!options.includes(currentInterim)) {
      setForm((prev) => ({ ...prev, interimRate: "", interimAmount: "" }));
    }
    if (contract === 100) {
      setForm((prev) => ({ ...prev, interimRate: "", interimAmount: "" }));
    }
  }, [form.contractRate]);

  // 계약금, 중도금, 잔금 금액 자동 계산 및 표시
  useEffect(() => {
    const contract = parseInt(form.contractRate) || 0;
    const interim = parseInt(form.interimRate) || 0;
    let balance = 100 - contract - interim;
    if (balance < 0) balance = 0;

    const toKoreanCurrency = (amount) => {
      const billion = Math.floor(amount / 100000000); // 억
      const million = Math.floor((amount % 100000000) / 10000); // 만 단위

      if (billion > 0 && million > 0)
        return `${billion}억 ${million.toLocaleString()}만원`;
      if (billion > 0) return `${billion}억`;
      if (million > 0) return `${million.toLocaleString()}만원`;
      return `${amount.toLocaleString()}원`;
    };

    const contractAmount = contract
      ? toKoreanCurrency((totalPrice * contract) / 100)
      : "";
    const interimAmount = interim
      ? toKoreanCurrency((totalPrice * interim) / 100)
      : "";
    const balanceAmount = totalPrice
      ? toKoreanCurrency((totalPrice * balance) / 100)
      : "";

    setForm((prev) => ({
      ...prev,
      balanceRate: `${balance}%`,
      contractAmount,
      interimAmount,
      balanceAmount,
    }));
  }, [form.contractRate, form.interimRate, totalPrice]);

  // 일반 입력 처리 및 유효성 검사
  const handleChange = (e) => {
    const { name, value } = e.target;
    const onlyNumber = /^[0-9]*$/;
    const onlyDecimal = /^[0-9]*\.?[0-9]*$/;
    const phonePattern = /^[0-9\-]*$/;

    const fieldMaxLength = {
      name: 15,
      scale: 30,
      builder: 10,
      contact: 13,
      tax: 12,
      supplyArea: 5,
      exclusiveArea: 5,
      rooms: 2,
      baths: 2,
      price1: 3,
      price2: 4,
    };

    if (fieldMaxLength[name] && value.length > fieldMaxLength[name]) return;

    if (["price1", "price2"].includes(name) && !onlyNumber.test(value)) return;

    if (
      ["supplyArea", "exclusiveArea"].includes(name) &&
      !onlyDecimal.test(value)
    )
      return;

    if (["price1", "price2", "tax"].includes(name)) {
      const numeric = removeComma(value);
      if (!onlyNumber.test(numeric)) return;
      setForm((prev) => ({ ...prev, [name]: formatWithComma(numeric) }));
      return;
    }

    if (name === "contact" && !phonePattern.test(value)) return;

    setForm((prev) => ({ ...prev, [name]: value }));
  };

  // 라디오 버튼 처리
  const handleRadio = (e) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  // 카카오 주소 검색 팝업
  const handleAddressSearch = () => {
    new window.daum.Postcode({
      oncomplete: function (data) {
        let fullAddr = data.address;
        if (data.addressType === "R") {
          if (data.bname !== "") fullAddr += " " + data.bname;
          if (data.buildingName !== "") fullAddr += " " + data.buildingName;
        }
        setForm((prev) => ({ ...prev, address: fullAddr }));
      },
    }).open();
  };

  // 이미지 파일 선택 처리 - 썸네일
  const handleThumbnailChange = (e) => {
    const files = Array.from(e.target.files);
    setThumbnailImages(files);
  };

  // 이미지 파일 선택 처리 - 평면도
  const handleFloorChange = (e) => {
    const files = Array.from(e.target.files);
    setFloorImages(files);
  };

  // 최종 수정 제출
  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitStatus("");
    if (
      !form.name ||
      !form.address ||
      !form.contact ||
      !form.price1 ||
      !form.price2 ||
      !form.contractRate ||
      !form.supplyArea ||
      !form.exclusiveArea ||
      thumbnailImages.length === 0 ||
      floorImages.length === 0
    ) {
      alert("필수 항목을 모두 입력해주세요.");
      return;
    }

    try {
      const response = await axios.get(
        `https://dapi.kakao.com/v2/local/search/address.json?query=${encodeURIComponent(
          form.address
        )}`,
        {
          headers: {
            Authorization: `KakaoAK ${import.meta.env.VITE_KAKAO_REST_API_KEY}`,
          },
        }
      );

      const result = response.data.documents[0];

      if (!result) throw new Error("주소에 해당하는 위치가 없습니다.");

      console.log("Kakao API 응답 result:", result); // ✅ 이 위치에서 출력해야 함

      // region_3depth_h_code 추출 (address 또는 road_address 중에서)
      const regionHCode = result.address?.h_code || result.road_address?.h_code;

      console.log("Kakao API 응답 result:", result);
      console.log("추출된 region_3depth_h_code:", regionHCode);

      if (!regionHCode || regionHCode.length < 5) {
        throw new Error(
          "법정동 코드(region_3depth_h_code)가 유효하지 않습니다."
        );
      }

      // 앞 5자리만 추출
      const regionNo = parseInt(regionHCode.substring(0, 5), 10);

      console.log("regionNo (앞 5자리):", regionNo);

      // 위도, 경도
      const lat = result.y;
      const lng = result.x;

      // 납입 금액 계산 (실제 숫자 값으로)
      const deposit = Math.floor(
        (totalPrice * (parseInt(form.contractRate) || 0)) / 100
      );
      const middlePayment = Math.floor(
        (totalPrice * (parseInt(form.interimRate) || 0)) / 100
      );
      const balancePayment = totalPrice - deposit - middlePayment;

      const saleData = {
        saleStockForm: typeMap[form.type], // 매물 형태 (1, 2, 3)
        saleStatus: statusMap[form.status], // 분양 상태 (1, 2, 3)
        saleStockName: form.name, // 매물명
        saleAddress: form.address, // 주소
        salePrice: totalPrice, // 총 분양가 (price1 + price2)
        scale: form.scale, // 규모
        applicationStartDate: form.startDate, // 청약 접수 시작일
        applicationEndDate: form.endDate, // 청약 접수 종료일
        announcementDate: form.recruitDate, // 당첨자 발표일
        company: form.builder, // 건설사
        contactInfo: form.contact, // 문의 연락처
        acquisitionTax: parseInt(removeComma(form.tax)), // 취득세
        saleSupplyArea: parseFloat(form.supplyArea), // 공급 면적
        saleExclusiveArea: parseFloat(form.exclusiveArea), // 전용 면적
        saleRoomCount: parseInt(form.rooms), // 방 개수
        saleBathroomCount: parseInt(form.baths), // 욕실 개수
        deposit, // 계약금
        middlePayment, // 중도금
        balancePayment, // 잔금
        regionNo: parseInt(regionNo), // 법정동 코드
        lat,
        lng,
      };

      console.log("전송할 saleData: ", saleData);
      console.log("region_3depth_h_code:", regionHCode);
      console.log("regionNo (5자리):", regionNo);
      console.log("좌표:", { lat, lng });

      const fd = new FormData();
      fd.append(
        "saleData",
        new Blob([JSON.stringify(saleData)], { type: "application/json" })
      );
      thumbnailImages.forEach((file) => fd.append("thumbnailImages", file));
      floorImages.forEach((file) => fd.append("floorImages", file));

      const res = await axios.put(
        `http://localhost:8080/admin/updateSale/${id}`,
        fd, // FormData 전송
        {
          withCredentials: true,
          headers: {
            "Content-Type": "multipart/form-data", // 명시적으로 설정 (사실 생략해도 axios가 자동 처리)
          },
        }
      );

      if (res.status === 200) {
        alert("분양 매물이 수정되었습니다.");
        navigate("/admin/list_sale");
        setSubmitStatus("수정이 완료되었습니다.");
        setForm(initialState);
        setThumbnailImages([]);
        setFloorImages([]);
      } else {
        alert("수정에 실패했습니다. 다시 시도해주세요.");
        setSubmitStatus("수정에 실패했습니다.");
      }
    } catch (error) {
      console.error(error);
      setSubmitStatus("서버 오류가 발생했습니다.");
    }
  };

  // 로딩 중일 때 표시
  if (loading) {
    return (
      <div className="up-register-form">
        <div style={{ textAlign: "center", padding: "50px" }}>
          <h2>매물 정보를 불러오는 중...</h2>
        </div>
      </div>
    );
  }

  return (
    <form className="up-register-form" onSubmit={handleSubmit}>
      {/* 기본정보 */}
      <section className="up-form-section">
        <h2 className="up-section-title">기본정보</h2>
        <div className="up-form-row">
          <label className="up-form-label required">매물형태</label>
          <div className="up-radio-group">
            {["아파트", "빌라", "오피스텔"].map((v) => (
              <label key={v}>
                <input
                  type="radio"
                  name="type"
                  value={v}
                  checked={form.type === v}
                  onChange={handleRadio}
                />
                {v}
              </label>
            ))}
          </div>
        </div>
        <div className="up-form-row">
          <label className="up-form-label required">매물명</label>
          <input
            type="text"
            name="name"
            placeholder="매물명을 입력해주세요 (20글자)"
            className="up-form-input"
            value={form.name}
            onChange={handleChange}
          />
        </div>
        <div className="up-form-row">
          <label className="up-form-label required">분양 상태</label>
          <div className="up-radio-group">
            {["분양예정", "분양중", "분양완료"].map((v) => (
              <label key={v}>
                <input
                  type="radio"
                  name="status"
                  value={v}
                  checked={form.status === v}
                  onChange={handleRadio}
                />
                {v}
              </label>
            ))}
          </div>
        </div>
      </section>
      {/* 상세정보 */}
      <section className="up-form-section">
        <h2 className="up-section-title">상세정보</h2>
        <div className="up-form-row">
          <label className="up-form-label required">분양주소</label>
          <div style={{ display: "flex", gap: "10px", flex: 1 }}>
            <input
              type="text"
              name="address"
              className="up-form-input"
              placeholder="주소를 검색해주세요"
              value={form.address}
              readOnly
            />
            <button
              type="button"
              className="up-adress-btn"
              onClick={handleAddressSearch}
            >
              주소 찾기
            </button>
          </div>
        </div>
        <div className="up-form-row">
          <label className="up-form-label">규모</label>
          <input
            type="text"
            name="scale"
            placeholder="규모를 입력해주세요 (30글자)"
            className="up-form-input"
            value={form.scale}
            onChange={handleChange}
          />
        </div>
        <div className="up-form-row">
          <label className="up-form-label">청약 접수 시작일</label>
          <input
            type="date"
            name="startDate"
            placeholder="청약 접수 시작일을 입력해주세요"
            className="up-form-input"
            value={form.startDate}
            onChange={handleChange}
          />
        </div>
        <div className="up-form-row">
          <label className="up-form-label">청약 접수 종료일</label>
          <input
            type="date"
            name="endDate"
            placeholder="청약 접수 종료일을 입력해주세요"
            className="up-form-input"
            value={form.endDate}
            onChange={handleChange}
          />
        </div>
        <div className="up-form-row">
          <label className="up-form-label">당첨자 발표일</label>
          <input
            type="date"
            name="recruitDate"
            placeholder="입주자 모집일을 입력해주세요"
            className="up-form-input"
            value={form.recruitDate}
            onChange={handleChange}
          />
        </div>
        <div className="up-form-row">
          <label className="up-form-label">건설사</label>
          <input
            type="text"
            name="builder"
            placeholder="건설사를 입력해주세요 (10글자)"
            className="up-form-input"
            value={form.builder}
            onChange={handleChange}
          />
        </div>
        <div className="up-form-row">
          <label className="up-form-label required">분양문의 연락처</label>
          <input
            type="text"
            name="contact"
            placeholder="연락처를 입력해주세요"
            className="up-form-input"
            value={form.contact}
            onChange={handleChange}
          />
        </div>
        <div className="up-form-row">
          <label className="up-form-label">취득세</label>
          <input
            type="text"
            name="tax"
            placeholder="취득세를 입력해주세요 (숫자만 입력해주세요)"
            className="up-form-input"
            value={form.tax}
            onChange={handleChange}
          />
        </div>
      </section>
      {/* 평형정보 */}
      <section className="up-form-section">
        <h2 className="up-section-title">평형정보</h2>
        <div className="up-form-row">
          <label className="up-form-label required">공급면적</label>
          <input
            type="text"
            name="supplyArea"
            placeholder="공급면적을 입력해주세요 (숫자만 입력해주세요)"
            className="up-form-input"
            value={form.supplyArea}
            onChange={handleChange}
          />
        </div>
        <div className="up-form-row">
          <label className="up-form-label required">전용면적</label>
          <input
            type="text"
            name="exclusiveArea"
            placeholder="전용면적을 입력해주세요 (숫자만 입력해주세요)"
            className="up-form-input"
            value={form.exclusiveArea}
            onChange={handleChange}
          />
        </div>
        <div className="up-form-row">
          <label className="up-form-label">방 개수</label>
          <input
            type="text"
            name="rooms"
            placeholder="방 개수를 입력해주세요"
            className="up-form-input"
            value={form.rooms}
            onChange={handleChange}
          />
        </div>
        <div className="up-form-row">
          <label className="up-form-label">욕실 수</label>
          <input
            type="text"
            name="baths"
            placeholder="욕실 개수를 입력해주세요"
            className="up-form-input"
            value={form.baths}
            onChange={handleChange}
          />
        </div>
      </section>
      {/* 납입정보 */}
      <section className="up-form-section">
        <h2 className="up-section-title">납입정보</h2>
        <div className="up-form-row">
          <label className="up-form-label required">분양가</label>
          <div style={{ display: "flex", gap: "10px", flex: 1 }}>
            <input
              type="text"
              name="price1"
              placeholder="(단위: 억)"
              className="up-form-input"
              value={form.price1}
              onChange={handleChange}
            />
            <input
              type="text"
              name="price2"
              placeholder="(단위: 만원)"
              className="up-form-input"
              value={form.price2}
              onChange={handleChange}
            />
          </div>
        </div>

        <div className="up-form-row">
          <label className="up-form-label">계약금</label>
          <select
            name="contractRate"
            className="up-form-input"
            value={form.contractRate}
            onChange={handleChange}
            style={{ flex: 1, marginRight: "10px" }}
          >
            <option value="">계약금 비율 선택</option>
            {[...Array(10)].map((_, i) => {
              const value = (i + 1) * 10;
              return (
                <option key={value} value={value}>
                  {value}%
                </option>
              );
            })}
          </select>
          <input
            type="text"
            name="contractAmount"
            className="up-form-input"
            value={form.contractAmount}
            readOnly
            style={{ flex: 1 }}
          />
        </div>

        <div className="up-form-row">
          <label className="up-form-label">중도금</label>
          <select
            name="interimRate"
            className="up-form-input"
            value={form.interimRate}
            onChange={handleChange}
            style={{ flex: 1, marginRight: "10px" }}
          >
            <option value="">중도금 비율 선택</option>
            {availableInterimRates.map((rate) => (
              <option key={rate} value={rate}>
                {rate}%
              </option>
            ))}
          </select>
          <input
            type="text"
            name="interimAmount"
            className="up-form-input"
            value={form.interimAmount}
            readOnly
            style={{ flex: 1 }}
          />
        </div>

        <div className="up-form-row">
          <label className="up-form-label">잔금</label>
          <input
            type="text"
            name="balanceRate"
            className="up-form-input"
            value={form.balanceRate}
            readOnly
            style={{ flex: 1, marginRight: "10px" }}
          />
          <input
            type="text"
            name="balanceAmount"
            className="up-form-input"
            value={form.balanceAmount}
            readOnly
            style={{ flex: 1 }}
          />
        </div>
      </section>
      <div className="up-form-row">
        <label className="up-form-label required">썸네일 이미지</label>
        <input
          type="file"
          accept="image/*"
          multiple
          onChange={handleThumbnailChange}
        />
        <div className="up-file-desc">
          * 썸네일 사진은 최대 1장, 5MB 까지 등록이 가능합니다.
        </div>
        {existingThumbnailNames.length > 0 && (
          <div className="up-file-existing">
            기존 파일: {existingThumbnailNames.join(", ")}
          </div>
        )}
      </div>

      <div className="up-form-row">
        <label className="up-form-label required">평면도 이미지</label>
        <input
          type="file"
          accept="image/*"
          multiple
          onChange={handleFloorChange}
        />
        <div className="up-file-desc">
          * 평면도 사진은 최대 1장, 5MB 까지 등록이 가능합니다.
        </div>
        {existingFloorNames.length > 0 && (
          <div className="up-file-existing">
            기존 파일: {existingFloorNames.join(", ")}
          </div>
        )}
      </div>
      <div className="up-submit-row">
        <button type="submit" className="up-submit-btn">
          수정하기
        </button>
        <button
          type="button"
          className="up-cancel-btn"
          onClick={() => window.history.back()}
        >
          취소하기
        </button>
      </div>
    </form>
  );
};

export default UpdateSale;
