import React from "react";
import SalePage from "./SalePage";
import { SaleProvider } from "./SaleContext";
import { useSearchParams, useNavigate } from "react-router-dom";

const SaleProviderWrapper = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  return (
    <SaleProvider searchParams={searchParams} navigate={navigate}>
      <SalePage />
    </SaleProvider>
  );
};

export default SaleProviderWrapper;
