import React from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { StockProvider } from "./StockContext";
import StockPageCopy from "./StockPageCopy";

const StockProviderWrapper = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  return (
    <StockProvider searchParams={searchParams} navigate={navigate}>
      <StockPageCopy />
    </StockProvider>
  );
};
export default StockProviderWrapper;
