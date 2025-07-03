import React from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { SaleProvider } from "./SaleContext";
import SalePage from "./SalePage";

// SalePage를 SaleProvider로 감싸고 queryString과 navigate를 전달
const SaleProviderWrapper = () => {
  const [searchParams] = useSearchParams(); // ?key=value 파싱용
  const navigate = useNavigate(); // 라우팅 이동용

  return (
    <SaleProvider searchParams={searchParams} navigate={navigate}>
      <SalePage />
    </SaleProvider>
  );
};

export default SaleProviderWrapper;
