import React, { useState, useEffect } from "react";
import "../../../css/admin/saleForm/addSale.css";

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
};

const AddSale = () => {
  const [form, setForm] = useState(initialState);
  const [submitStatus, setSubmitStatus] = useState("");
  const [availableInterimRates, setAvailableInterimRates] = useState([]);

  const formatWithComma = (val) => val.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  const removeComma = (val) => val.replace(/,/g, "");

  const totalPrice =
    Number(form.price1 || 0) * 10000 + Number(form.price2 || 0);

  useEffect(() => {
    const contract = parseInt(form.contractRate) || 0;
    const interim = parseInt(form.interimRate) || 0;
    if (contract > 0) {
      const interimOptions = [];
      for (let i = 10; i <= 100 - contract; i += 10) {
        interimOptions.push(i);
      }
      setAvailableInterimRates(interimOptions);
    } else {
      setAvailableInterimRates([]);
    }
  }, [form.contractRate]);

  useEffect(() => {
    const contract = parseInt(form.contractRate) || 0;
    const interim = parseInt(form.interimRate) || 0;
    let balance = 100 - contract - interim;

    if (balance < 0) {
      balance = 0;
    }

    const formatKoreanCurrency = (amount) => {
      const billion = Math.floor(amount / 10000);
      const million = amount % 10000;
      if (billion > 0 && million > 0) {
        return `${billion}억 ${million.toLocaleString()}만원`;
      } else if (billion > 0) {
        return `${billion}억`;
      } else {
        return `${million.toLocaleString()}만원`;
      }
    };

    const contractAmount =
      contract > 0 && totalPrice > 0
        ? formatKoreanCurrency((totalPrice * contract) / 100)
        : "";
    const interimAmount =
      interim > 0 && totalPrice > 0
        ? formatKoreanCurrency((totalPrice * interim) / 100)
        : "";
    const balanceAmount =
      totalPrice > 0 ? formatKoreanCurrency((totalPrice * balance) / 100) : "";

    setForm((prev) => ({
      ...prev,
      balanceRate: `${balance}%`,
      contractAmount,
      interimAmount,
      balanceAmount,
    }));
  }, [form.contractRate, form.interimRate, totalPrice]);

  const handleChange = (e) => {
    const { name, value } = e.target;

    // 입력 제한 적용
    const onlyNumber = /^[0-9]*$/;
    const phonePattern = /^[0-9\-]*$/;

    const fieldMaxLength = {
      name: 20,
      address: 20,
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

    if (
      ["supplyArea", "exclusiveArea", "price1", "price2"].includes(name) &&
      !onlyNumber.test(value)
    )
      return;

    if (name === "tax") {
      const numeric = removeComma(value);
      if (!onlyNumber.test(numeric)) return;
      setForm((prev) => ({
        ...prev,
        tax: formatWithComma(numeric),
      }));
      return;
    }
    if (name === "contact" && !phonePattern.test(value)) return;

    setForm((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleRadio = (e) => {
    setForm((prev) => ({
      ...prev,
      [e.target.name]: e.target.value,
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitStatus("");

    // 필수 항목 검증 개별 처리
    if (!form.name) {
      alert("매물명을 입력해주세요.");
      return;
    }
    if (!form.address) {
      alert("분양 주소를 입력해주세요.");
      return;
    }
    if (!form.contact) {
      alert("연락처를 입력해주세요.");
      return;
    }
    if (!form.price1) {
      alert("분양가(억 단위)를 입력해주세요.");
      return;
    }
    if (!form.price2) {
      alert("분양가(만원 단위)를 입력해주세요.");
      return;
    }

    try {
      const response = await fetch("/admin/add", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(form),
      });
      if (response.ok) {
        setSubmitStatus("등록이 완료되었습니다.");
        setForm(initialState);
      } else {
        setSubmitStatus("등록에 실패했습니다.");
      }
    } catch (error) {
      setSubmitStatus("서버 오류가 발생했습니다.");
    }
  };

  return (
    <form className="sale-register-form" onSubmit={handleSubmit}>
      {/* 기본정보 */}
      <section className="sale-form-section">
        <h2 className="sale-section-title">기본정보</h2>
        <div className="sale-form-row">
          <label className="sale-form-label required">매물형태</label>
          <div className="sale-radio-group">
            {["아파트", "주택/빌라", "오피스텔"].map((v) => (
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
        <div className="sale-form-row">
          <label className="sale-form-label rquirede">매물명</label>
          <input
            type="text"
            name="name"
            placeholder="매물명을 입력해주세요 (20글자)"
            className="sale-form-input"
            value={form.name}
            onChange={handleChange}
          />
        </div>
        <div className="sale-form-row">
          <label className="sale-form-label required">분양 상태</label>
          <div className="sale-radio-group">
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
      <section className="sale-form-section">
        <h2 className="sale-section-title">상세정보</h2>
        <div className="sale-form-row">
          <label className="sale-form-label required">분양주소</label>
          <input
            type="text"
            name="address"
            placeholder="분양 주소를 입력해주세요 (20글자)"
            className="sale-form-input"
            value={form.address}
            onChange={handleChange}
          />
        </div>
        <div className="sale-form-row">
          <label className="sale-form-label">규모</label>
          <input
            type="text"
            name="scale"
            placeholder="규모를 입력해주세요 (30글자)"
            className="sale-form-input"
            value={form.scale}
            onChange={handleChange}
          />
        </div>
        <div className="sale-form-row">
          <label className="sale-form-label">청약 접수 시작일</label>
          <input
            type="date"
            name="startDate"
            placeholder="청약 접수 시작일을 입력해주세요"
            className="sale-form-input"
            value={form.startDate}
            onChange={handleChange}
          />
        </div>
        <div className="sale-form-row">
          <label className="sale-form-label">청약 접수 종료일</label>
          <input
            type="date"
            name="endDate"
            placeholder="청약 접수 종료일을 입력해주세요"
            className="sale-form-input"
            value={form.endDate}
            onChange={handleChange}
          />
        </div>
        <div className="sale-form-row">
          <label className="sale-form-label">당첨자 발표일</label>
          <input
            type="date"
            name="recruitDate"
            placeholder="입주자 모집일을 입력해주세요"
            className="sale-form-input"
            value={form.recruitDate}
            onChange={handleChange}
          />
        </div>
        <div className="sale-form-row">
          <label className="sale-form-label">건설사</label>
          <input
            type="text"
            name="builder"
            placeholder="건설사를 입력해주세요 (10글자)"
            className="sale-form-input"
            value={form.builder}
            onChange={handleChange}
          />
        </div>
        <div className="sale-form-row">
          <label className="sale-form-label required">분양문의 연락처</label>
          <input
            type="text"
            name="contact"
            placeholder="연락처를 입력해주세요"
            className="sale-form-input"
            value={form.contact}
            onChange={handleChange}
          />
        </div>
        <div className="sale-form-row">
          <label className="sale-form-label">취득세</label>
          <input
            type="text"
            name="tax"
            placeholder="취득세를 입력해주세요 (숫자만 입력해주세요)"
            className="sale-form-input"
            value={form.tax}
            onChange={handleChange}
          />
        </div>
      </section>

      {/* 평형정보 */}
      <section className="sale-form-section">
        <h2 className="sale-section-title">평형정보</h2>
        <div className="sale-form-row">
          <label className="sale-form-label">공급면적</label>
          <input
            type="text"
            name="supplyArea"
            placeholder="공급면적을 입력해주세요 (숫자만 입력해주세요)"
            className="sale-form-input"
            value={form.supplyArea}
            onChange={handleChange}
          />
        </div>
        <div className="sale-form-row">
          <label className="sale-form-label">전용면적</label>
          <input
            type="text"
            name="exclusiveArea"
            placeholder="전용면적을 입력해주세요 (숫자만 입력해주세요)"
            className="sale-form-input"
            value={form.exclusiveArea}
            onChange={handleChange}
          />
        </div>
        <div className="sale-form-row">
          <label className="sale-form-label">방수</label>
          <input
            type="text"
            name="rooms"
            placeholder="방 개수를 입력해주세요"
            className="sale-form-input"
            value={form.rooms}
            onChange={handleChange}
          />
        </div>
        <div className="sale-form-row">
          <label className="sale-form-label">욕실 수</label>
          <input
            type="text"
            name="baths"
            placeholder="욕실 개수를 입력해주세요"
            className="sale-form-input"
            value={form.baths}
            onChange={handleChange}
          />
        </div>
      </section>

      {/* 납입정보 */}
      <section className="sale-form-section">
        <h2 className="sale-section-title">납입정보</h2>
        <div className="sale-form-row">
          <label className="sale-form-label required">분양가</label>
          <div style={{ display: "flex", gap: "10px", flex: 1 }}>
            <input
              type="text"
              name="price1"
              placeholder="(단위: 억)"
              className="sale-form-input"
              value={form.price1}
              onChange={handleChange}
            />
            <input
              type="text"
              name="price2"
              placeholder="(단위: 만원)"
              className="sale-form-input"
              value={form.price2}
              onChange={handleChange}
            />
          </div>
        </div>

        <div className="sale-form-row">
          <label className="sale-form-label">계약금</label>
          <select
            name="contractRate"
            className="sale-form-input"
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
            className="sale-form-input"
            value={form.contractAmount}
            readOnly
            style={{ flex: 1 }}
          />
        </div>

        <div className="sale-form-row">
          <label className="sale-form-label">중도금</label>
          <select
            name="interimRate"
            className="sale-form-input"
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
            className="sale-form-input"
            value={form.interimAmount}
            readOnly
            style={{ flex: 1 }}
          />
        </div>

        <div className="sale-form-row">
          <label className="sale-form-label">잔금</label>
          <input
            type="text"
            name="balanceRate"
            className="sale-form-input"
            value={form.balanceRate}
            readOnly
            style={{ flex: 1, marginRight: "10px" }}
          />
          <input
            type="text"
            name="balanceAmount"
            className="sale-form-input"
            value={form.balanceAmount}
            readOnly
            style={{ flex: 1 }}
          />
        </div>
      </section>

      {/* 첨부파일 */}
      <section className="sale-form-section">
        <div className="sale-form-row">
          <label className="sale-form-label">첨부파일</label>
          <button type="button" className="sale-file-btn">
            + 파일추가
          </button>
          <div className="sale-file-desc">
            * 사진은 최대 10장까지 첨부 가능합니다.
            <br />* 사진은 10MB 이하의 파일만 첨부 가능합니다.
          </div>
        </div>
        <div className="sale-form-row">
          <label className="sale-form-label">청약 이미지</label>
          <button type="button" className="sale-file-btn">
            + 파일추가
          </button>
          <div className="sale-file-desc">
            * 사진은 최대 10장까지 첨부 가능합니다.
            <br />* 사진은 10MB 이하의 파일만 첨부 가능합니다.
          </div>
        </div>
      </section>

      {/* 등록 및 취소 버튼 */}
      <div className="sale-submit-row">
        <button type="submit" className="sale-submit-btn">
          등록하기
        </button>
        <button
          type="button"
          className="sale-cancel-btn"
          onClick={() => window.history.back()}
        >
          취소하기
        </button>
      </div>
    </form>
  );
};

export default AddSale;
