import { useParams } from "react-router-dom";

const NeighborhoodEdit = () => {
  const boardNo = useParams();

  const isEdit = Boolean(boardNo);
  // isEdit이 참이면 글이 있는거니까 수정 화면을 띄어주고, 거짓이면 글이 없는거니까 빈 로직을 띄워준다.
};

export default NeighborhoodEdit;
