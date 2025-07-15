import React from "react";
import ImageGalleryModal from "./ImageGalleryModal";

const DetailPage = () => {
  const item = {
    title: "예시 아이템",
    imgUrl: ["/images/img1.jpg", "/images/img2.jpg", "/images/img3.jpg"],
  };

  return (
    <div>
      <h2>{item.title}</h2>
      <ImageGalleryModal item={item} />
    </div>
  );
};

export default DetailPage;
