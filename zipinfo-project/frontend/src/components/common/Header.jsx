import { Link, useNavigate } from "react-router-dom";
import { useContext } from "react";
import logo from "../../assets/logo.svg";
import "../../css/common/Header.css";
import { MemberContext } from "../member/MemberContext";

const Header = () => {
  const { member, setMember } = useContext(MemberContext);
  const navigate = useNavigate();

  // 로그아웃 => 로컬스토리지 초기화
  const handleLogout = async () => {
    // 0) 백엔드 세션(JSESSIONID) 무효화
    try {
      // Spring 컨트롤러에 만든 로그아웃 엔드포인트 (예: /member/logout)
      await axiosAPI.post("/member/logout"); //  경로는 프로젝트에 맞춰 조정
    } catch (_) {
      /* 세션이 이미 없으면 401/404가 올 수 있으니 무시 */
    }
    // 1) 카카오 SDK 로그아웃
    if (window.Kakao && window.Kakao.Auth) {
      window.Kakao.Auth.logout(() => {
        console.log("Kakao SDK 로그아웃 완료");
      });
    }

    // 2) 앱 내부 상태·스토리지 초기화
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
            <Link to="/stock">실거래가</Link>
          </li>
          <li>
            <Link to="/sale">분양</Link>
          </li>
          <li>
            <Link to="/announce">공지사항</Link>
          </li>
          <li>
            <Link to="/neighborhoodBoard">우리동네</Link>
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
