<#list blocks as block>
    ════════════════════════════════════════
    ${date}  ${college}  ${block.building}宿舍楼
    ════════════════════════════════════════
    <#if showSummary>
        应到  ${block.summary.should?string("0")?left_pad(5)} 人    实到  ${block.summary.actual?string("0")?left_pad(5)} 人
        请假  ${block.summary.leave?string("0")?left_pad(5)} 人    走读  ${block.summary.dayStudent?string("0")?left_pad(5)} 人
    </#if>
    <#if showFloorDetail>
        ────────────────────────────────────────
        楼层     应到     实到     请假     实习
        <#list block.floors as floor>
            ${floor.floor?right_pad(6)} ${floor.should?string("0")?left_pad(6)} ${floor.actual?string("0")?left_pad(8)} ${floor.leave?string("0")?left_pad(8)} ${floor.intern?string("0")?left_pad(8)}
        </#list>
    </#if>

</#list>
<#if showNewStudent && newStudent??>
    ════════════════════════════════════════
    新生数据
    ════════════════════════════════════════
    应到  ${newStudent.should} 人    实到  ${newStudent.actual} 人
    男生  ${newStudent.male} 人    女生  ${newStudent.female} 人
    征兵  ${newStudent.military} 人    请假  ${newStudent.leave} 人
</#if>