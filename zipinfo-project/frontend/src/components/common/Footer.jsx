import "../../css/common/Footer.css";

const Footer = () => {
  return (
    <footer className="footer">
      <div className="footer-menu">
        <a href="#">프로젝트 소개</a>
        <a href="#">이용약관</a>
        <a href="#" className="privacy-policy">
          개인정보처리방침
        </a>
        <a href="#">고객센터</a>
      </div>
      <div className="footer-info">
        <div>
          상호명 : 집인포 <span>|</span> 대표 : 김용제
        </div>
        <div>사업자등록번호 : 123-45-67890</div>
        <div>
          주소 : 서울특별시 중구 남대문로 120 그레이츠 청계구 대일빌딩 2F,3F
        </div>
        <div>전화 : 1234-5678</div>
      </div>
      <div className="footer-copy">
        Copyright © 1998-2025 KH Information Educational Institute All Right
        Reserved
      </div>
    </footer>
  );
};

export default Footer;
