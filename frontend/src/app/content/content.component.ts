import {Component, OnInit, ViewChild} from "@angular/core";
import {WidgetService} from "../services/widget.service";
import {HttpService} from "../services/http.service";
import {MoskitoApplicationService} from "../services/moskito-application.service";
import {Configuration} from "../entities/old/configuration";
import {TimerComponent} from "../shared/timer/timer.component";
import {MoskitoApplication} from "../entities/moskito-application";


@Component({
  selector: 'content',
  templateUrl: './content.component.html'
})
export class ContentComponent implements OnInit {

  configToggle: boolean;

  configuration: Configuration;

  applications: MoskitoApplication[];
  applicationDataLoaded: boolean;

  @ViewChild('dataRefreshTimer')
  timer: TimerComponent;


  constructor(private widgetService: WidgetService, private httpService: HttpService, private moskitoApplicationService: MoskitoApplicationService) {
    this.applicationDataLoaded = false;
  }

  public ngOnInit(): void {

    // Getting list of all aplications
    this.httpService.getMoskitoApplications().subscribe((applications) => {
      this.applications = applications;
      this.moskitoApplicationService.currentApplication = applications[0];

      this.applicationDataLoaded = true;
    });

    // Initializing timer
    setTimeout(() => {
      this.timer.callback = this.onDataRefresh.bind(this);
      this.timer.startTimer();
    }, 1000);
  }

  /**
   * Handler is called by data refresh timer each 60 seconds.
   * It refreshes all Moskito Control data without reload.
   */
  public onDataRefresh() {
    console.log('[Refresh Handler]: %s. Testing data refresh!', new Date());
    this.moskitoApplicationService.refreshData();
  }

  public setConfigurationMode(mode: boolean) {
    this.configToggle = mode;
  }

  public setApplication(app: MoskitoApplication) {
    this.moskitoApplicationService.currentApplication = app;
    this.onDataRefresh();
  }

  keys(): Array<any> {
    return Object.keys(this.applications);
  }
}