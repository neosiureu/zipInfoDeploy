// src/components/common/Header.jsx
import { Link } from "react-router-dom";
import logo from "../../assets/logo.svg";
import "../../css/common/Header.css";

const Header = () => {
  return (
    <header className="navbar">
      <div className="navbar-left">
        <span className="logo">
          <Link to="/">
            <img src={logo} alt="로고이미지" />
          </Link>
        </span>
        <ul className="menu">
          <li>
            <Link to="/realprice">실거래가</Link>
          </li>
          <li>
            <Link to="/sale">분양</Link>
          </li>
          <li>
            <Link to="/notice">공지사항</Link>
          </li>
          <li>
            <Link to="/help">관심목록</Link>
          </li>
          <li>
            <Link to="/interest">우리동네</Link>
          </li>
        </ul>
      </div>
      <div className="navbar-right">
        <ul className="member">
          <li id="signup-btn">회원가입</li>
          <li id="login-btn">로그인</li>
        </ul>
      </div>
    </header>
  );
};

export default Header;
