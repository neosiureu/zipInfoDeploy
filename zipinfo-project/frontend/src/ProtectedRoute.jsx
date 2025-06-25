import { useContext, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { MemberContext } from "./components/member/MemberContext";

const ProtectedRoute = ({ children }) => {
  const { member } = useContext(MemberContext);
  const navigate = useNavigate();
  const alerted = useRef(false); // Stric Mode에서 alert창이 두번 떠서 어쩔수 없이 이걸 써야 함

  useEffect(() => {
    if (!member && !alerted.current) {
      alerted.current = true; // 플래그 설정
      alert("로그인 후 이용해주세요");
      navigate("/login", { replace: true });
    }
  }, [member, navigate]);

  return member ? children : null;
};

export default ProtectedRoute;
