package com.zipinfo.ai.tools;

import com.zipinfo.ai.data.AddressBook;
import com.zipinfo.ai.data.History;
import com.zipinfo.ai.data.TradeStore;
import com.zipinfo.ai.data.ValidityTable;

/** 도구가 읽는 자료 넷. 한 번 읽어 모든 실행이 같이 쓴다(읽기만 한다). */
public record DataSet(TradeStore trades, ValidityTable validity, History history, AddressBook addresses) {}
