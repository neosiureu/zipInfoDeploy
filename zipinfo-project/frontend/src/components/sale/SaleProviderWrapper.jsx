import React from "react";
import SalePage from "./SalePage";
import { SaleProvider } from "./SaleContext";

const SaleProviderWrapper = () => {
  return (
    <SaleProvider>
      <SalePage />
    </SaleProvider>
  );
};

export default SaleProviderWrapper;
