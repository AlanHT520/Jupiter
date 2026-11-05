<#list blocks as block>
    ${date}${college}${block.building}宿舍楼查宿情况
    <#if showSummary>
        应到:${block.summary.should}<#if showNewStudent && block.summary.newShould gt 0>（新生${block.summary.newShould}）</#if>
        实到:${block.summary.actual}<#if showNewStudent && block.summary.newActual gt 0>（新生${block.summary.newActual}）</#if>
        请假:${block.summary.leave}<#if showNewStudent && block.summary.newLeave gt 0>（新生${block.summary.newLeave}）</#if>
        <#if block.summary.military gt 0>征兵:${block.summary.military}<#if showNewStudent && block.summary.newMilitary gt 0>（新生${block.summary.newMilitary}）</#if></#if>
        <#if block.summary.dayStudent gt 0>走读:${block.summary.dayStudent}</#if>
    </#if>
    <#if showFloorDetail>
        <#list block.floors as floor>
            ${floor.floor}:应到：${floor.should}人 实到：${floor.actual}<#if floor.leave gt 0> 请假：${floor.leave}人</#if><#if floor.military gt 0> 征兵：${floor.military}人</#if><#if floor.intern gt 0> 实习：${floor.intern}人</#if>
            <#if showNewStudent && (floor.newShould gt 0 || floor.newActual gt 0 || floor.newLeave gt 0 || floor.newMilitary gt 0 || floor.newIntern gt 0)>
                新生：应到：${floor.newShould}人 实到：${floor.newActual}人<#if floor.newLeave gt 0> 请假：${floor.newLeave}人</#if><#if floor.newMilitary gt 0> 征兵：${floor.newMilitary}人</#if><#if floor.newIntern gt 0> 实习：${floor.newIntern}人</#if>
            </#if>
        </#list>
    </#if>

</#list>