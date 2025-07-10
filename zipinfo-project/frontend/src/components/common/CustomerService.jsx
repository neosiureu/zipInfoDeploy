import React from "react";

export default function CustomerService() {
  return (
    <div
      className="terms-container"
      style={{
        maxWidth: 800,
        margin: "0 auto",
        padding: 32,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        minHeight: "40vh",
      }}
    >
      <style>{`
        .terms-container h2,
        .terms-container p {
          margin-bottom: 12px;
        }
        .customer-contact {
          font-size: 18px;
          font-weight: 500;
        }
      `}</style>
      <h2>** 고객센터 **</h2>
      <div className="customer-contact">02-123-4567</div>
    </div>
  );
}
