import { Link, useNavigate } from "react-router-dom";
import { useContext } from "react";
import logo from "../../assets/logo.svg";
import "../../css/common/Header.css";
import { MemberContext } from "../member/MemberContext";

const Header = () => {
  const { member, setMember } = useContext(MemberContext);
  const navigate = useNavigate();

  // 로그아웃 => 로컬스토리지 초기화
  const handleLogout = () => {
    setMember(null);
    localStorage.removeItem("loginMember");
    alert("로그아웃 되었습니다");
    navigate("/");
  };

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
            <Link to="/help">문의하기</Link>
          </li>
          <li>
            <Link to="/interest">관심목록</Link>
          </li>
        </ul>
      </div>

      <div className="navbar-right">
        <ul className="member">
          {member ? (
            member.memberAuth == 0 ? (
              <>
                <li id="admin-page">
                  <Link to="/admin">관리자 페이지로 이동</Link>
                </li>
                <li id="logout-btn">
                  <button onClick={handleLogout}>로그아웃</button>
                </li>
              </>
            ) : (
              <>
                <li id="my-page">
                  <Link to="/myPage">마이페이지</Link>
                </li>
                <li id="logout-btn">
                  <button onClick={handleLogout}>로그아웃</button>
                </li>
              </>
            )
          ) : (
            <>
              <li id="signup-btn">
                <Link to="/signUp">회원가입</Link>
              </li>
              <li id="login-btn">
                <Link to="/login">로그인</Link>
              </li>
            </>
          )}
        </ul>
      </div>
    </header>
  );
};

export default Header;
