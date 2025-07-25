import { useContext, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { MemberContext } from "./components/member/MemberContext";

const ProtectedRoute = ({ children }) => {
  const { member } = useContext(MemberContext);
  const navigate = useNavigate();
  const alerted = useRef(false); // Stric Mode에서 alert창이 두번 떠서 어쩔수 없이 이걸 써야 함. 한번 실행되었는지 일종의 플래그를 설정하는 것

  useEffect(() => {
    if (member && !alerted.current) {
      if (member.memberAuth === 0) {
        alerted.current = true; // 플래그 설정
        alert("관리자는 접속할 수 없습니다.");
        navigate("/", { replace: true });
      }
    }
  }, [member, navigate]);

  return member ? children : null;
};

export default ProtectedRoute;
