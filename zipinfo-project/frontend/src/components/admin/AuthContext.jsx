import axios from "axios";
import { createContext, useState, useMemo } from "react";

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    const storeUser = localStorage.getItem("userData");
    return storeUser ? JSON.parse(storeUser) : null;
  });
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  // 관리자 여부 계산
  const isAdmin = useMemo(() => {
    return user && user.memberRole === "ADMIN"; // memberRole 필드명과 값은 실제 데이터에 맞게 조정하세요
  }, [user]);

  const changeInputEmail = (e) => {
    setEmail(e.target.value);
  };

  const changeInputPw = (e) => {
    setPassword(e.target.value);
  };

  const handleLogin = async (e) => {
    e.preventDefault();

    try {
      const response = await axios.post("http://localhost/admin/login", {
        memberEmail: email,
        memberPw: password,
      });

      console.log("로그인 응답:", response.data);

      const adminInfo = Array.isArray(response.data)
        ? response.data[0]
        : response.data;

      if (!adminInfo || !adminInfo.memberRole) {
        alert("이메일 혹은 비밀번호 불일치!");
        return;
      }

      setUser(adminInfo);
      localStorage.setItem("userData", JSON.stringify(adminInfo));

      setTimeout(() => {
        localStorage.removeItem("userData");
        setUser(null);
        alert("재로그인 해주세요~");
        window.location.href = "/";
      }, 60 * 60 * 1000);
    } catch (error) {
      console.error("로그인 실패:", error);
      alert("로그인 중 오류 발생!");
    }
  };

  const handleLogout = async () => {
    try {
      const resp = await axios.get("http://localhost/admin/logout");
      if (resp.status === 200) {
        localStorage.removeItem("userData");
        setUser(null);
      }
    } catch (error) {
      console.log("로그아웃 중 문제발생 : ", error);
    }
  };

  const globalState = {
    user,
    isAdmin, // 여기 추가!
    changeInputEmail,
    changeInputPw,
    handleLogin,
    handleLogout,
  };

  return (
    <AuthContext.Provider value={globalState}>{children}</AuthContext.Provider>
  );
};
